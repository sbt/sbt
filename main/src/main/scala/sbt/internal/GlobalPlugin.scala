/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
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
import sbt.internal.util.Attributed
import Def.{ ScopedKey, Setting }
import Keys._
import Configurations.{ Compile, Runtime }
import java.io.File
import org.apache.ivy.core.module.{ descriptor, id }
import descriptor.ModuleDescriptor, id.ModuleRevisionId

object GlobalPlugin {
  // constructs a sequence of settings that may be appended to a project's settings to
  //  statically add the global plugin as a classpath dependency.
  //  static here meaning that the relevant tasks for the global plugin have already been evaluated
  def inject(gp: GlobalPluginData): Seq[Setting[_]] =
    Seq[Setting[_]](
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
  private[this] def injectInternalClasspath(config: Configuration,
                                            cp: Seq[Attributed[File]]): Setting[_] =
    internalDependencyClasspath in config ~= { prev =>
      (prev ++ cp).distinct
    }

  def build(base: File, s: State, config: LoadBuildConfiguration): (BuildStructure, State) = {
    val newInject =
      config.injectSettings.copy(global = config.injectSettings.global ++ globalPluginSettings)
    val globalConfig = config.copy(injectSettings = newInject,
                                   pluginManagement = config.pluginManagement.forGlobalPlugin)
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
    val p: Scope = Scope.GlobalScope in ProjectRef(root, rootProject(root))

    val taskInit = Def.task {
      val intcp = (internalDependencyClasspath in Runtime).value
      val prods = (exportedProducts in Runtime).value
      val depMap = projectDescriptors.value + ivyModule.value.dependencyMapping(state.log)
      // If we reference it directly (if it's an executionRoot) then it forces an update, which is not what we want.
      val updateReport = Def.taskDyn { Def.task { update.value } }.value

      GlobalPluginData(projectID.value,
                       projectDependencies.value,
                       depMap,
                       resolvers.value.toVector,
                       (fullClasspath in Runtime).value,
                       (prods ++ intcp).distinct)(updateReport)
    }
    val resolvedTaskInit = taskInit mapReferenced Project.mapScope(Scope replaceThis p)
    val task = resolvedTaskInit evaluate data
    val roots = resolvedTaskInit.dependencies
    evaluate(state, structure, task, roots)
  }
  def evaluate[T](state: State,
                  structure: BuildStructure,
                  t: Task[T],
                  roots: Seq[ScopedKey[_]]): (State, T) = {
    import EvaluateTask._
    withStreams(structure, state) { str =>
      val nv = nodeView(state, str, roots)
      val config = EvaluateTask.extractedTaskConfig(Project.extract(state), structure, state)
      val (newS, result) = runTask(t, state, str, structure.index.triggers, config)(nv)
      (newS, processResult(result, newS.log))
    }
  }
  val globalPluginSettings = Project.inScope(Scope.GlobalScope in LocalRootProject)(
    Seq(
      organization := SbtArtifacts.Organization,
      onLoadMessage := Keys.baseDirectory("Loading global plugins from " + _).value,
      name := "global-plugin",
      sbtPlugin := true,
      version := "0.0"
    ))
}
final case class GlobalPluginData(projectID: ModuleID,
                                  dependencies: Seq[ModuleID],
                                  descriptors: Map[ModuleRevisionId, ModuleDescriptor],
                                  resolvers: Vector[Resolver],
                                  fullClasspath: Classpath,
                                  internalClasspath: Classpath)(val updateReport: UpdateReport)
final case class GlobalPlugin(data: GlobalPluginData,
                              structure: BuildStructure,
                              inject: Seq[Setting[_]],
                              base: File)
