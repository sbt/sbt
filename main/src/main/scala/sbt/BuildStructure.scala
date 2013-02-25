/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import java.net.URI
	import Def.{displayFull, ScopedKey, ScopeLocal, Setting}
	import Attributed.data
	import BuildPaths.outputDirectory
	import Scope.GlobalScope
	import BuildStreams.Streams
	import Path._

final class BuildStructure(val units: Map[URI, LoadedBuildUnit], val root: URI, val settings: Seq[Setting[_]], val data: Settings[Scope], val index: StructureIndex, val streams: State => Streams, val delegates: Scope => Seq[Scope], val scopeLocal: ScopeLocal)
{
	val rootProject: URI => String = Load getRootProject units
	def allProjects: Seq[ResolvedProject] = units.values.flatMap(_.defined.values).toSeq
	def allProjects(build: URI): Seq[ResolvedProject] = units.get(build).toList.flatMap(_.defined.values)
	def allProjectRefs: Seq[ProjectRef] = units.toSeq flatMap { case (build, unit) => refs(build, unit.defined.values.toSeq) }
	def allProjectRefs(build: URI): Seq[ProjectRef] = refs(build, allProjects(build))
	val extra: BuildUtil[ResolvedProject] = BuildUtil(root, units, index.keyIndex, data)
	private[this] def refs(build: URI, projects: Seq[ResolvedProject]): Seq[ProjectRef] = projects.map { p => ProjectRef(build, p.id) }
}
// information that is not original, but can be reconstructed from the rest of BuildStructure
final class StructureIndex(
	val keyMap: Map[String, AttributeKey[_]],
	val taskToKey: Map[Task[_], ScopedKey[Task[_]]],
	val triggers: Triggers[Task],
	val keyIndex: KeyIndex,
	val aggregateKeyIndex: KeyIndex
)
final class LoadedBuildUnit(val unit: BuildUnit, val defined: Map[String, ResolvedProject], val rootProjects: Seq[String], val buildSettings: Seq[Setting[_]]) extends BuildUnitBase
{
	assert(!rootProjects.isEmpty, "No root projects defined for build unit " + unit)
	val root = rootProjects.head
	def localBase = unit.localBase
	def classpath: Seq[File] = unit.definitions.target ++ unit.plugins.classpath
	def loader = unit.definitions.loader
	def imports = BuildUtil.getImports(unit)
	override def toString = unit.toString
}

final class LoadedDefinitions(val base: File, val target: Seq[File], val loader: ClassLoader, val builds: Seq[Build], val projects: Seq[Project], val buildNames: Seq[String])
final class LoadedPlugins(val base: File, val pluginData: PluginData, val loader: ClassLoader, val plugins: Seq[Plugin], val pluginNames: Seq[String])
{
	def fullClasspath: Seq[Attributed[File]] = pluginData.classpath
	def classpath = data(fullClasspath)
}
final class BuildUnit(val uri: URI, val localBase: File, val definitions: LoadedDefinitions, val plugins: LoadedPlugins)
{
	override def toString = if(uri.getScheme == "file") localBase.toString else (uri + " (locally: " + localBase +")")
}

final class LoadedBuild(val root: URI, val units: Map[URI, LoadedBuildUnit])
{
	BuildUtil.checkCycles(units)
	def allProjectRefs: Seq[(ProjectRef, ResolvedProject)] = for( (uri, unit) <- units.toSeq; (id, proj) <- unit.defined ) yield ProjectRef(uri, id) -> proj
	def extra(data: Settings[Scope])(keyIndex: KeyIndex): BuildUtil[ResolvedProject] = BuildUtil(root, units, keyIndex, data)
}
final class PartBuild(val root: URI, val units: Map[URI, PartBuildUnit])
sealed trait BuildUnitBase { def rootProjects: Seq[String]; def buildSettings: Seq[Setting[_]] }
final class PartBuildUnit(val unit: BuildUnit, val defined: Map[String, Project], val rootProjects: Seq[String], val buildSettings: Seq[Setting[_]]) extends BuildUnitBase
{
	def resolve(f: Project => ResolvedProject): LoadedBuildUnit = new LoadedBuildUnit(unit, defined mapValues f toMap, rootProjects, buildSettings)
	def resolveRefs(f: ProjectReference => ProjectRef): LoadedBuildUnit = resolve(_ resolve f)
}

object BuildStreams
{
	type Streams = std.Streams[ScopedKey[_]]

	final val GlobalPath = "$global"
	final val BuildUnitPath = "$build"
	final val StreamsDirectory = "streams"

	def mkStreams(units: Map[URI, LoadedBuildUnit], root: URI, data: Settings[Scope]): State => Streams = s =>
		std.Streams( path(units, root, data), displayFull, LogManager.construct(data, s) )
		
	def path(units: Map[URI, LoadedBuildUnit], root: URI, data: Settings[Scope])(scoped: ScopedKey[_]): File =
		resolvePath( projectPath(units, root, scoped, data), nonProjectPath(scoped) )

	def resolvePath(base: File, components: Seq[String]): File =
		(base /: components)( (b,p) => new File(b,p) )

	def pathComponent[T](axis: ScopeAxis[T], scoped: ScopedKey[_], label: String)(show: T => String): String =
		axis match
		{
			case Global => GlobalPath
			case This => sys.error("Unresolved This reference for " + label + " in " + displayFull(scoped))
			case Select(t) => show(t)
		}
	def nonProjectPath[T](scoped: ScopedKey[T]): Seq[String] =
	{
		val scope = scoped.scope
		pathComponent(scope.config, scoped, "config")(_.name) ::
		pathComponent(scope.task, scoped, "task")(_.label) ::
		pathComponent(scope.extra, scoped, "extra")(showAMap) ::
		Nil
	}
	def showAMap(a: AttributeMap): String =
		a.entries.toSeq.sortBy(_.key.label).map { case AttributeEntry(key, value) => key.label + "=" + value.toString } mkString(" ")
	def projectPath(units: Map[URI, LoadedBuildUnit], root: URI, scoped: ScopedKey[_], data: Settings[Scope]): File =
		scoped.scope.project match
		{
			case Global => refTarget(GlobalScope, units(root).localBase, data) / GlobalPath
			case Select(br @ BuildRef(uri)) => refTarget(br, units(uri).localBase, data) / BuildUnitPath
			case Select(pr @ ProjectRef(uri, id)) => refTarget(pr, units(uri).defined(id).base, data)
			case Select(pr) => sys.error("Unresolved project reference (" + pr + ") in " + displayFull(scoped))
			case This => sys.error("Unresolved project reference (This) in " + displayFull(scoped))
		}
		
	def refTarget(ref: ResolvedReference, fallbackBase: File, data: Settings[Scope]): File =
		refTarget(GlobalScope.copy(project = Select(ref)), fallbackBase, data)
	def refTarget(scope: Scope, fallbackBase: File, data: Settings[Scope]): File =
		(Keys.target in scope get data getOrElse outputDirectory(fallbackBase).asFile ) / StreamsDirectory
}