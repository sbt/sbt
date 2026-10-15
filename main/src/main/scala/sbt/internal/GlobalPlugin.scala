/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.File
import java.net.URI
import sbt.io.IO
import xsbti.{ FileConverter, VirtualFile, VirtualFileRef }
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

object GlobalPlugin:
  // constructs a sequence of settings that may be appended to a project's settings to
  //  statically add the global plugin as a classpath dependency.
  //  static here meaning that the relevant tasks for the global plugin have already been evaluated
  def inject(gp: GlobalPluginData): Seq[Setting[?]] =
    // A classes directory reaches the meta-build only through its internal classpath.
    val gpDepsWithJar = gp.exportedJarUris.headOption match
      case Some(uri) if IO.toFile(uri).isFile => gp.projectID.from(uri) +: gp.dependencies
      case _                                  => gp.dependencies
    Seq[Setting[?]](
      projectDependencies ++= Def.uncached(gpDepsWithJar),
      resolvers := {
        val rs = resolvers.value
        (rs ++ gp.resolvers).distinct
      },
      globalPluginUpdate := Def.uncached(gp.updateReport),
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

  /** Converts a classpath `cp` encoded with `src` to be read with `dst`. */
  private[sbt] def reencode(
      cp: Def.Classpath,
      src: FileConverter,
      dst: FileConverter
  ): Def.Classpath =
    def move[R >: VirtualFile <: VirtualFileRef](ref: R): R =
      val path = src.toPath(ref)
      if dst.toPath(VirtualFileRef.of(ref.id)) == path then ref else dst.toVirtualFile(path)
    cp.map: entry =>
      val moved = entry.map(move)
      moved.get(Keys.analysis) match
        case Some(id) => moved.put(Keys.analysis, move(VirtualFileRef.of(id)).id)
        case None     => moved

  def build(base: File, s: State, config: LoadBuildConfiguration): (BuildStructure, State) =
    val newInject =
      config.injectSettings.copy(global = config.injectSettings.global ++ globalPluginSettings)
    val globalConfig = config.copy(
      injectSettings = newInject,
      pluginManagement = config.pluginManagement.forGlobalPlugin
    )
    val (eval, structure) = Load(base, s, globalConfig)
    val session = Load.initialSession(structure, eval)
    (structure, Project.setProject(session, structure, s))
  def load(base: File, s: State, config: LoadBuildConfiguration): GlobalPlugin =
    val (structure, state) = build(base, s, config)
    val (newS, data) = extract(state, structure)
    Project.runUnloadHooks(newS) // discard state
    GlobalPlugin(data, structure, inject(data), base)

  def extract(state: State, structure: BuildStructure): (State, GlobalPluginData) =
    import structure.{ data, root, rootProject }
    val p: Scope = Scope.GlobalScope.rescope(ProjectRef(root, rootProject(root)))

    // If we reference it directly (if it's an executionRoot) then it forces an update, which is not what we want.
    val updateReport = (Def.task { () }).flatMapTask { case _ => Def.task { update.value } }
    val taskInit = Def.task {
      val intcp = (Runtime / internalDependencyClasspath).value
      val prods = (Runtime / exportedProducts).value
      val converter = fileConverter.value
      val exportedJarUris = prods.map(a => converter.toPath(a.data).toUri).toVector

      GlobalPluginData(
        projectID.value,
        projectDependencies.value,
        resolvers.value.toVector,
        (Runtime / fullClasspath).value,
        (prods ++ intcp).distinct,
        exportedJarUris
      )(updateReport.value, converter)
    }
    val resolvedTaskInit = taskInit.mapReferenced(Project.replaceThis(p))
    val task = resolvedTaskInit.evaluate(data)
    val roots = resolvedTaskInit.dependencies
    evaluate(state, structure, task, roots)
  end extract
  def evaluate[T](
      state: State,
      structure: BuildStructure,
      t: Task[T],
      roots: Seq[ScopedKey[?]]
  ): (State, T) =
    import EvaluateTask.*
    withStreams(structure, state) { str =>
      val nv = nodeView(state, str, roots)
      val config = EvaluateTask.extractedTaskConfig(Project.extract(state), structure, state)
      val (newS, result) = runTask(t, state, str, structure.index.triggers, config)(using nv)
      (newS, processResult2(result))
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
end GlobalPlugin
final case class GlobalPluginData(
    projectID: ModuleID,
    dependencies: Seq[ModuleID],
    resolvers: Vector[Resolver],
    fullClasspath: Classpath,
    internalClasspath: Classpath,
    exportedJarUris: Vector[URI],
)(val updateReport: UpdateReport, val converter: FileConverter)
final case class GlobalPlugin(
    data: GlobalPluginData,
    structure: BuildStructure,
    inject: Seq[Setting[?]],
    base: File
)
