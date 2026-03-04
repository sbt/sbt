/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.librarymanagement.{
  Configuration,
  Configurations,
  ModuleID,
  Resolver,
  SbtArtifacts,
  UpdateReport
}
import Def.{ ScopedKey, Setting }
import Keys.*
import Configurations.{ Compile, Runtime }
import sbt.ProjectExtra.{ extract, runUnloadHooks, setProject }
import sbt.SlashSyntax0.*
import sbt.librarymanagement.LibraryManagementCodec.given
import java.io.File

object GlobalPlugin {
  // constructs a sequence of settings that may be appended to a project's settings to
  //  statically add the global plugin as a classpath dependency.
  //  static here meaning that the relevant tasks for the global plugin have already been evaluated
  def inject(gp: GlobalPluginData): Seq[Setting[?]] =
    Seq[Setting[?]](
      projectDescriptors ~= { _ ++ gp.descriptors },
      projectDependencies ++= gp.projectID +: gp.dependencies,
      resolvers := {
        val rs = resolvers.value
        (rs ++ gp.resolvers).distinct
      },
      globalPluginUpdate := gp.updateReport,
      // TODO: these shouldn't be required (but are): the project* settings above should take care of this
      injectInternalClasspath(Runtime, gp.internalClasspath),
      injectInternalClasspath(Compile, gp.internalClasspath)
    )
  private def injectInternalClasspath(
      config: Configuration,
      cp: Def.Classpath,
  ): Setting[?] =
    (config / internalDependencyClasspath) ~= { prev =>
      (prev ++ cp).distinct
    }

  def build(base: File, s: State, config: LoadBuildConfiguration): (BuildStructure, State) = {
    val newInject =
      config.injectSettings.copy(global = config.injectSettings.global ++ globalPluginSettings)
    val globalConfig = config.copy(
      injectSettings = newInject,
      pluginManagement = config.pluginManagement.forGlobalPlugin
    )
    val (eval, structure) = Load(base, s, globalConfig)
    val session = Load.initialSession(structure, eval)
    (structure, Project.setProject(session, structure, s))
  }
  def load(base: File, s: State, config: LoadBuildConfiguration): GlobalPlugin = {
    val (structure, state) = build(base, s, config)
    val (newS, data) = extract(state, structure)
    Project.runUnloadHooks(newS) // discard state
    GlobalPlugin(data, structure, inject(data), base)
  }

  def extract(state: State, structure: BuildStructure): (State, GlobalPluginData) = {
    import structure.{ data, root, rootProject }
    val p: Scope = Scope.GlobalScope.rescope(ProjectRef(root, rootProject(root)))

    // If we reference it directly (if it's an executionRoot) then it forces an update, which is not what we want.
    val updateReport = (Def.task { () }).flatMapTask { case _ => Def.task { update.value } }
    val taskInit = Def.task {
      val intcp = (Runtime / internalDependencyClasspath).value
      val prods = (Runtime / exportedProducts).value
      val depMap = projectDescriptors.value

      GlobalPluginData(
        projectID.value,
        projectDependencies.value,
        depMap,
        resolvers.value.toVector,
        (Runtime / fullClasspath).value,
        (prods ++ intcp).distinct
      )(updateReport.value)
    }
    val resolvedTaskInit = taskInit.mapReferenced(Project.replaceThis(p))
    val task = resolvedTaskInit.evaluate(data)
    val roots = resolvedTaskInit.dependencies
    evaluate(state, structure, task, roots)
  }
  def evaluate[T](
      state: State,
      structure: BuildStructure,
      t: Task[T],
      roots: Seq[ScopedKey[?]]
  ): (State, T) = {
    import EvaluateTask.*
    withStreams(structure, state) { str =>
      val nv = nodeView(state, str, roots)
      val config = EvaluateTask.extractedTaskConfig(Project.extract(state), structure, state)
      val (newS, result) = runTask(t, state, str, structure.index.triggers, config)(using nv)
      (newS, processResult2(result))
    }
  }

  val globalPluginSettings = Project.inScope(Scope.GlobalScope.rescope(LocalRootProject))(
    Seq(
      organization := SbtArtifacts.Organization,
      onLoadMessage := Keys.baseDirectory("loading global plugins from " + _).value,
      name := "global-plugin",
      sbtPlugin := true,
      version := "0.0"
    )
  )
}
final case class GlobalPluginData(
    projectID: ModuleID,
    dependencies: Seq[ModuleID],
    descriptors: Map[Any, Any],
    resolvers: Vector[Resolver],
    fullClasspath: Classpath,
    internalClasspath: Classpath
)(val updateReport: UpdateReport)
final case class GlobalPlugin(
    data: GlobalPluginData,
    structure: BuildStructure,
    inject: Seq[Setting[?]],
    base: File
)
