/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal

import java.io.File
import java.net.URI
import Def.{ displayFull, ScopedKey, ScopeLocal, Setting }
import BuildPaths.outputDirectory
import Scope.GlobalScope
import BuildStreams.Streams
import sbt.io.syntax._
import sbt.internal.util.{ Attributed, AttributeEntry, AttributeKey, AttributeMap, Settings }
import sbt.internal.util.Attributed.data
import sbt.util.Logger
import sjsonnew.shaded.scalajson.ast.unsafe.JValue

final class BuildStructure(val units: Map[URI, LoadedBuildUnit],
                           val root: URI,
                           val settings: Seq[Setting[_]],
                           val data: Settings[Scope],
                           val index: StructureIndex,
                           val streams: State => Streams,
                           val delegates: Scope => Seq[Scope],
                           val scopeLocal: ScopeLocal) {
  val rootProject: URI => String = Load getRootProject units
  def allProjects: Seq[ResolvedProject] = units.values.flatMap(_.defined.values).toSeq
  def allProjects(build: URI): Seq[ResolvedProject] =
    units.get(build).toList.flatMap(_.defined.values)
  def allProjectRefs: Seq[ProjectRef] = units.toSeq flatMap {
    case (build, unit) => refs(build, unit.defined.values.toSeq)
  }
  def allProjectRefs(build: URI): Seq[ProjectRef] = refs(build, allProjects(build))
  val extra: BuildUtil[ResolvedProject] = BuildUtil(root, units, index.keyIndex, data)
  private[this] def refs(build: URI, projects: Seq[ResolvedProject]): Seq[ProjectRef] =
    projects.map { p =>
      ProjectRef(build, p.id)
    }
}
// information that is not original, but can be reconstructed from the rest of BuildStructure
final class StructureIndex(
    val keyMap: Map[String, AttributeKey[_]],
    val taskToKey: Map[Task[_], ScopedKey[Task[_]]],
    val triggers: Triggers[Task],
    val keyIndex: KeyIndex,
    val aggregateKeyIndex: KeyIndex
)

/**
 * A resolved build unit.  (`ResolvedBuildUnit` would be a better name to distinguish it from the loaded, but unresolved `BuildUnit`.)
 * @param unit The loaded, but unresolved [[BuildUnit]] this was resolved from.
 * @param defined The definitive map from project IDs to resolved projects.
 *                These projects have had [[Reference]]s resolved and [[AutoPlugin]]s evaluated.
 * @param rootProjects The list of project IDs for the projects considered roots of this build.
 *                The first root project is used as the default in several situations where a project is not otherwise selected.
 */
final class LoadedBuildUnit(val unit: BuildUnit,
                            val defined: Map[String, ResolvedProject],
                            val rootProjects: Seq[String],
                            val buildSettings: Seq[Setting[_]])
    extends BuildUnitBase {

  /**
   * The project to use as the default when one is not otherwise selected.
   * [[LocalRootProject]] resolves to this from within the same build.
   */
  val root = rootProjects match {
    case Nil =>
      throw new java.lang.AssertionError(
        "assertion failed: No root projects defined for build unit " + unit)
    case Seq(root, _*) => root
  }

  /** The base directory of the build unit (not the build definition).*/
  def localBase = unit.localBase

  /**
   * The classpath to use when compiling against this build unit's publicly visible code.
   * It includes build definition and plugin classes and classes for .sbt file statements and expressions.
   */
  def classpath: Seq[File] =
    unit.definitions.target ++ unit.plugins.classpath ++ unit.definitions.dslDefinitions.classpath

  /**
   * The class loader to use for this build unit's publicly visible code.
   * It includes build definition and plugin classes and classes for .sbt file statements and expressions.
   */
  def loader = unit.definitions.dslDefinitions.classloader(unit.definitions.loader)

  /** The imports to use for .sbt files, `consoleProject` and other contexts that use code from the build definition. */
  def imports = BuildUtil.getImports(unit)
  override def toString = unit.toString
}

// TODO: figure out how to deprecate and drop buildNames
/**
 * The built and loaded build definition, including loaded but unresolved [[Project]]s, for a build unit (for a single URI).
 *
 * @param base The base directory of the build definition, typically `<build base>/project/`.
 * @param loader The ClassLoader containing all classes and plugins for the build definition project.
 *               Note that this does not include classes for .sbt files.
 * @param builds The list of [[BuildDef]]s for the build unit.
 *               In addition to auto-discovered [[BuildDef]]s, this includes any auto-generated default [[BuildDef]]s.
 * @param projects The list of all [[Project]]s from all [[BuildDef]]s.
 *                 These projects have not yet been resolved, but they have had auto-plugins applied.
 *                 In particular, each [[Project]]'s `autoPlugins` field is populated according to their configured `plugins`
 *                 and their `settings` and `configurations` updated as appropriate.
 * @param buildNames No longer used and will be deprecated once feasible.
 */
final class LoadedDefinitions(
    val base: File,
    val target: Seq[File],
    val loader: ClassLoader,
    val builds: Seq[BuildDef],
    val projects: Seq[Project],
    val buildNames: Seq[String],
    val dslDefinitions: DefinedSbtValues
) {
  def this(
      base: File,
      target: Seq[File],
      loader: ClassLoader,
      builds: Seq[BuildDef],
      projects: Seq[Project],
      buildNames: Seq[String]
  ) = this(base, target, loader, builds, projects, buildNames, DefinedSbtValues.empty)
}

/** Auto-detected top-level modules (as in `object X`) of type `T` paired with their source names. */
final class DetectedModules[T](val modules: Seq[(String, T)]) {

  /**
   * The source names of the modules.  This is "X" in `object X`, as opposed to the implementation class name "X$".
   * The names are returned in a stable order such that `names zip values` pairs a name with the actual module.
   */
  def names: Seq[String] = modules.map(_._1)

  /**
   * The singleton value of the module.
   * The values are returned in a stable order such that `names zip values` pairs a name with the actual module.
   */
  def values: Seq[T] = modules.map(_._2)
}

/** Auto-detected auto plugin. */
case class DetectedAutoPlugin(name: String, value: AutoPlugin, hasAutoImport: Boolean)

/**
 * Auto-discovered modules for the build definition project.  These include modules defined in build definition sources
 * as well as modules in binary dependencies.
 *
 * @param builds The [[Build]]s detected in the build definition.  This does not include the default [[Build]] that sbt creates if none is defined.
 */
final class DetectedPlugins(val autoPlugins: Seq[DetectedAutoPlugin],
                            val builds: DetectedModules[BuildDef]) {

  /**
   * Sequence of import expressions for the build definition.
   *  This includes the names of the [[BuildDef]], and [[DetectedAutoPlugin]] modules,
   *  but not the [[AutoPlugin]] modules.
   */
  lazy val imports: Seq[String] = BuildUtil.getImports(builds.names) ++
    BuildUtil.importAllRoot(autoImports(autoPluginAutoImports)) ++
    BuildUtil.importAll(autoImports(topLevelAutoPluginAutoImports)) ++
    BuildUtil.importNamesRoot(autoPlugins.map(_.name).filter(nonTopLevelPlugin))

  private[this] lazy val (autoPluginAutoImports, topLevelAutoPluginAutoImports) =
    autoPlugins
      .flatMap {
        case DetectedAutoPlugin(name, ap, hasAutoImport) =>
          if (hasAutoImport) Some(name)
          else None
      }
      .partition(nonTopLevelPlugin)

  /** Selects the right [[AutoPlugin]]s from a [[Project]]. */
  def deducePluginsFromProject(p: Project, log: Logger): Seq[AutoPlugin] = {
    val ps0 = p.plugins
    val allDetected = autoPlugins.toList map { _.value }
    val detected = p match {
      case _: GeneratedRootProject => allDetected filterNot { _ == sbt.plugins.IvyPlugin }
      case _                       => allDetected
    }
    Plugins.deducer(detected)(ps0, log)
  }

  private[this] def autoImports(pluginNames: Seq[String]) = pluginNames.map(_ + ".autoImport")

  private[this] def nonTopLevelPlugin(name: String) = name.contains('.')
}

/**
 * The built and loaded build definition project.
 * @param base The base directory for the build definition project (not the base of the project itself).
 * @param pluginData Evaluated tasks/settings from the build definition for later use.
 *                   This is necessary because the build definition project is discarded.
 * @param loader The class loader for the build definition project, notably excluding classes used for .sbt files.
 * @param detected Auto-detected modules in the build definition.
 */
final class LoadedPlugins(val base: File,
                          val pluginData: PluginData,
                          val loader: ClassLoader,
                          val detected: DetectedPlugins) {
  def fullClasspath: Seq[Attributed[File]] = pluginData.classpath
  def classpath = data(fullClasspath)
}

/**
 * The loaded, but unresolved build unit.
 * @param uri The uniquely identifying URI for the build.
 * @param localBase The working location of the build on the filesystem.
 *        For local URIs, this is the same as `uri`, but for remote URIs, this is the local copy or workspace allocated for the build.
 */
final class BuildUnit(val uri: URI,
                      val localBase: File,
                      val definitions: LoadedDefinitions,
                      val plugins: LoadedPlugins) {
  override def toString =
    if (uri.getScheme == "file") localBase.toString else (uri + " (locally: " + localBase + ")")
}

final class LoadedBuild(val root: URI, val units: Map[URI, LoadedBuildUnit]) {
  BuildUtil.checkCycles(units)
  def allProjectRefs: Seq[(ProjectRef, ResolvedProject)] =
    for ((uri, unit) <- units.toSeq; (id, proj) <- unit.defined) yield ProjectRef(uri, id) -> proj
  def extra(data: Settings[Scope])(keyIndex: KeyIndex): BuildUtil[ResolvedProject] =
    BuildUtil(root, units, keyIndex, data)

  private[sbt] def autos = GroupedAutoPlugins(units)
}
final class PartBuild(val root: URI, val units: Map[URI, PartBuildUnit])
sealed trait BuildUnitBase { def rootProjects: Seq[String]; def buildSettings: Seq[Setting[_]] }
final class PartBuildUnit(val unit: BuildUnit,
                          val defined: Map[String, Project],
                          val rootProjects: Seq[String],
                          val buildSettings: Seq[Setting[_]])
    extends BuildUnitBase {
  def resolve(f: Project => ResolvedProject): LoadedBuildUnit =
    new LoadedBuildUnit(unit, defined mapValues f toMap, rootProjects, buildSettings)
  def resolveRefs(f: ProjectReference => ProjectRef): LoadedBuildUnit = resolve(_ resolve f)
}

object BuildStreams {
  type Streams = std.Streams[ScopedKey[_]]

  final val GlobalPath = "$global"
  final val BuildUnitPath = "$build"
  final val StreamsDirectory = "streams"

  def mkStreams(units: Map[URI, LoadedBuildUnit],
                root: URI,
                data: Settings[Scope]): State => Streams = s => {
    implicit val isoString: sjsonnew.IsoString[JValue] =
      sjsonnew.IsoString.iso(sjsonnew.support.scalajson.unsafe.CompactPrinter.apply,
                             sjsonnew.support.scalajson.unsafe.Parser.parseUnsafe)
    (s get Keys.stateStreams) getOrElse {
      std.Streams(path(units, root, data),
                  displayFull,
                  LogManager.construct(data, s),
                  sjsonnew.support.scalajson.unsafe.Converter)
    }
  }

  def path(units: Map[URI, LoadedBuildUnit], root: URI, data: Settings[Scope])(
      scoped: ScopedKey[_]): File =
    resolvePath(projectPath(units, root, scoped, data), nonProjectPath(scoped))

  def resolvePath(base: File, components: Seq[String]): File =
    (base /: components)((b, p) => new File(b, p))

  def pathComponent[T](axis: ScopeAxis[T], scoped: ScopedKey[_], label: String)(
      show: T => String): String =
    axis match {
      case Zero => GlobalPath
      case This =>
        sys.error("Unresolved This reference for " + label + " in " + displayFull(scoped))
      case Select(t) => show(t)
    }
  def nonProjectPath[T](scoped: ScopedKey[T]): Seq[String] = {
    val scope = scoped.scope
    pathComponent(scope.config, scoped, "config")(_.name) ::
      pathComponent(scope.task, scoped, "task")(_.label) ::
      pathComponent(scope.extra, scoped, "extra")(showAMap) ::
      scoped.key.label ::
      Nil
  }
  def showAMap(a: AttributeMap): String =
    a.entries.toSeq.sortBy(_.key.label).map {
      case AttributeEntry(key, value) => key.label + "=" + value.toString
    } mkString (" ")
  def projectPath(units: Map[URI, LoadedBuildUnit],
                  root: URI,
                  scoped: ScopedKey[_],
                  data: Settings[Scope]): File =
    scoped.scope.project match {
      case Zero                             => refTarget(GlobalScope, units(root).localBase, data) / GlobalPath
      case Select(br @ BuildRef(uri))       => refTarget(br, units(uri).localBase, data) / BuildUnitPath
      case Select(pr @ ProjectRef(uri, id)) => refTarget(pr, units(uri).defined(id).base, data)
      case Select(pr) =>
        sys.error("Unresolved project reference (" + pr + ") in " + displayFull(scoped))
      case This => sys.error("Unresolved project reference (This) in " + displayFull(scoped))
    }

  def refTarget(ref: ResolvedReference, fallbackBase: File, data: Settings[Scope]): File =
    refTarget(GlobalScope.copy(project = Select(ref)), fallbackBase, data)
  def refTarget(scope: Scope, fallbackBase: File, data: Settings[Scope]): File =
    (Keys.target in scope get data getOrElse outputDirectory(fallbackBase).asFile) / StreamsDirectory
}
