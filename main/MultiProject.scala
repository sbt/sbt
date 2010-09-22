/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import std._
import Path._
import TaskExtra._
import GlobFilter._
import Transform.Context
import inc.Analysis
import build.{Auto, Build}
import xsbti.AppConfiguration

import java.io.File
import annotation.tailrec

object MultiProject
{
	val InitialProject = AttributeKey[Project]("initial-project")

	val defaultExcludes: FileFilter = (".*"  - ".") || HiddenFileFilter
	def descendents(base: PathFinder, select: FileFilter) = base.descendentsExcept(select, defaultExcludes)
	def children(base: PathFinder, select: FileFilter) = base * (select -- defaultExcludes)
	def projectClassName = classOf[Project].getName

	def projectStandard(base: Path) = base / "project"
	def projectHidden(base: Path) = base / ".sbt"
	def selectProjectDir(base: Path) =
	{
		val a = projectHidden(base)
		val b = projectStandard(base)
		if(a.exists) a else b
	}

	def crossPath(base: Path, instance: ScalaInstance) = base / ("scala_" + instance.actualVersion)

	def construct[T <: AnyRef](arg: T)(implicit mf: Manifest[T]): Class[_] => Any = clazz =>
		Build.constructor(clazz, mf.erasure) match {
			case Some(c) => c.newInstance(arg)
			case None => error("Couldn't find constructor with one argument of type " + mf.toString)
		}

	// externals must not be evaluated until after _all_ projects have been loaded
	def load(configuration: AppConfiguration, log: Logger, externals: ExternalProjects)(base: File): Project =
	{
		val projectDir = selectProjectDir(base)
		val buildDir = projectDir / "build"
		val srcMain = buildDir / "src" / "main"
		val javaSrcBase = srcMain / "java"
		val sources = children(buildDir +++ projectDir, "*.scala") +++ descendents(buildDir / "scala" +++ buildDir / "java", "*.scala" | "*.java")
		val classpath = configuration.provider.mainClasspath.toSeq
		val compilers = Compile.compilers(configuration, log)
		val target = crossPath(buildDir / "target", compilers.scalac.scalaInstance)
		val inputs = Compile.inputs(classpath, sources.getFiles.toSeq, target, Nil, Nil, javaSrcBase :: Nil, Compile.DefaultMaxErrors)(compilers, log)

		val analysis = Compile(inputs)
		val info = ProjectInfo(None, base, projectDir, Nil, None)(configuration, analysis, inputs, load(configuration, log, externals), externals)

		val discovered = Build.check(Build.discover(analysis, Some(false), Auto.Subclass, projectClassName), false)
		Build.binaries(inputs.config.classpath, discovered, getClass.getClassLoader)(construct(info)).head.asInstanceOf[Project]
	}

	def loadExternals(from: Iterable[Project], loadImpl: File => Project): Map[File, Project] =
	{
		def load(loaded: Map[File, Project], file: File): Map[File, Project] =
			(loaded get file) match
			{
				case None => doLoad(loaded, file)
				case Some(p) => loaded
			}
		def doLoad(loaded: Map[File, Project], file: File): Map[File, Project] =
		{
			val loadedProject = loadImpl(file)
			val newMap = loaded.updated(file, loadedProject)
			loadAll( externals(loadedProject :: Nil), newMap )
		}
		def loadAll(files: Set[File], loaded: Map[File, Project]): Map[File, Project] = (loaded /: files)(load)

		loadAll(  externals(from) , Map.empty)
	}

	def externals(containers: Iterable[Project]): Set[File] =
	{
		def exts(containers: Iterable[Project]): Iterable[File] =
			containers flatMap { container => externalProjects(container) ++ exts(internalProjects(container)) }
		exts(containers).toSet
	}
	def externalProjects(p: Project) = lefts( allDependencies(p) )
	def internalProjects(p: Project) = rights( allDependencies(p) )
	def allDependencies(p: Project) = (p.aggregate ++ p.dependencies).map(_.project)

	def internalTopologicalSort(root: Project): Seq[Project] =
		Dag.topologicalSort(root)(internalProjects)

	def topologicalSort(root: Project): Seq[Project] = topologicalSort(root, root.info.externals)
	def topologicalSort(root: Project, resolveExternal: File => Project): Seq[Project] =
		Dag.topologicalSort(root) { p =>
			(externalProjects(p) map resolveExternal) ++ internalProjects(p)
		}
	def initialProject(state: State, ifUnset: => Project): Project =state get MultiProject.InitialProject getOrElse ifUnset

	def makeContext(root: Project) =
	{
		val contexts = topologicalSort(root) map { p => (p, ReflectiveContext(p, p.name)) }
		val externals = root.info.externals
		def subs(f: Project => Iterable[ProjectDependency]): Project =>  Iterable[Project] = p =>
			f(p) map( _.project match { case Left(path) => externals(path); case Right(proj) => proj } )
		MultiContext(contexts)(subs(_.aggregate), subs(_.dependencies) )
	}

	def lefts[A,B](e: Iterable[Either[A,B]]):Iterable[A] = e collect { case Left(l) => l }
	def rights[A,B](e: Iterable[Either[A,B]]):Iterable[B] = e collect { case Right(r)=> r }
}

object MultiContext
{
	def identityMap[A,B](in: Iterable[(A,B)]): collection.Map[A,B] =
	{
		import collection.JavaConversions._
		val map: collection.mutable.Map[A, B] = new java.util.IdentityHashMap[A,B]
		for( (a,b) <- in) map(a) = b
		map
	}
	def fun[A,B](map: collection.Map[A,B]): A => Option[B] = map get _
	def apply[Owner <: AnyRef](contexts: Iterable[(Owner, Context[Owner])])(agg: Owner => Iterable[Owner], deps: Owner => Iterable[Owner]): Context[Owner] = new Context[Owner]
	{
		val ownerReverse = (for( (owner, context) <- contexts; name <- context.ownerName(owner).toList) yield (name, owner) ).toMap
		val ownerMap = identityMap[Task[_],Owner]( for((owner, context) <- contexts; task <- context.allTasks(owner) ) yield (task, owner) )
		val context = identityMap(contexts)
		def subMap(f: Owner => Iterable[Owner]) = identityMap( contexts.map { case (o,_) => (o,f(o)) })
		val aggMap = subMap(agg)
		val depMap = subMap(deps)

		def allTasks(owner: Owner): Iterable[Task[_]] = context(owner).allTasks(owner)
		def ownerForName(name: String): Option[Owner] = ownerReverse get name
		val staticName: Task[_] => Option[String] = t => owner(t) flatMap { o => context(o).staticName(t) }
		val ownerName = (o: Owner) => context(o).ownerName(o)
		val owner = (t: Task[_]) => ownerMap.get(t)
		val aggregate = (o: Owner) => (aggMap get o).toList.flatten
		def dependencies(o: Owner): Iterable[Owner] = (depMap get o).toList.flatten
		val static = (o: Owner, s: String) => context(o).static(o, s)
	}
}

trait Project extends Tasked with HistoryEnabled with Member[Project] with Named with ConsoleTask
{
	val info: ProjectInfo

	def settings: Settings = Settings.empty
	def name: String = info.name getOrElse error("'name' not overridden")

	def base = info.projectDirectory
	def outputRootPath = base / "target"
	def historyPath = Some(outputRootPath / ".history")
	def streamBase = outputRootPath / "streams"

	def projectClosure(s: State): Seq[Project] = MultiProject.topologicalSort(MultiProject.initialProject(s, rootProject))
	@tailrec final def rootProject: Project = info.parent match { case Some(p) => p.rootProject; case None => this }

	implicit def streams = Dummy.Streams
	def input = Dummy.In
	def state = Dummy.State

	def aggregate: Iterable[ProjectDependency.Execution] = info.dependencies collect { case ex: ProjectDependency.Execution => ex }
	def dependencies: Iterable[ProjectDependency.Classpath] = info.dependencies collect { case cp: ProjectDependency.Classpath => cp }

	type Task[T] = sbt.Task[T]
	def act(input: Input, state: State): Option[(Task[State], Execute.NodeView[Task])] =
	{
		import Dummy._
		val context = MultiProject.makeContext(this)
		val dummies = new Transform.Dummies(In, State, Streams)
		def name(t: Task[_]): String = context.staticName(t.original) getOrElse std.Streams.name(t)
		val mklog = LogManager.construct(context, settings)
		val actualStreams = std.Streams(t => context.owner(t.original).get.streamBase / name(t), mklog )
		val injected = new Transform.Injected( input, state, actualStreams )
		context.static(this, input.name) map { t => (t.merge.map(_ => state), Transform(dummies, injected, context) ) }
	}

	def help: Seq[Help] = Nil
}
trait ProjectExtra
{
	val info: ProjectInfo
	/** Converts a String to a path relative to the project directory of this project. */
	implicit def path(component: String): Path = info.projectDirectory / component
	/** Converts a String to a simple name filter.  * has the special meaning: zero or more of any character */
	implicit def filter(simplePattern: String): NameFilter = GlobFilter(simplePattern)
}
trait ReflectiveProject extends Project
{
	private[this] def vals[T: Manifest] = ReflectUtilities.allVals[T](this).map(_._2)
	override def aggregate: Iterable[ProjectDependency.Execution] = vals[ProjectDependency.Execution] ++ vals[Project].map(p => ProjectDependency.Execution(Right(p))) ++ super.aggregate
	override def dependencies: Iterable[ProjectDependency.Classpath] = vals[ProjectDependency.Classpath] ++ super.dependencies
}
trait ConsoleTask
{
	val info: ProjectInfo
	lazy val projectConsole = task { Console.sbtDefault(info.compileInputs, this)(ConsoleLogger()) }
}

trait ProjectConstructors extends Project
{
	val info: ProjectInfo
	//def project(base: Path, name: String, deps: ProjectDependency*): Project = project(path, name, info => new DefaultProject(info), deps: _* )
	def project[P <: Project](path: Path, name: String, construct: ProjectInfo => P, deps: ProjectDependency*): P =
		construct( info.copy(Some(name), projectDirectory = path.asFile, parent = Some(this), dependencies = deps)() )

	def project(base: Path): ProjectDependency.Execution = new ProjectDependency.Execution(Left(base.asFile))

	implicit def defaultProjectDependency(p: Project): ProjectDependency = new ProjectDependency.Classpath(Right(p), None)

	implicit def dependencyConstructor(p: Project): ProjectDependencyConstructor = dependencyConstructor(Right(p))
	implicit def extDependencyConstructor(p: File): ProjectDependencyConstructor = dependencyConstructor(Left(p))
	implicit def extDependencyConstructor(p: ProjectDependency.Execution): ProjectDependencyConstructor = dependencyConstructor(p.project)

	def dependencyConstructor(p: Either[File, Project]): ProjectDependencyConstructor = new ProjectDependencyConstructor {
		def %(conf: String) = new ProjectDependency.Classpath(p, Some(conf))
	}
}
sealed trait ProjectDependency { val project: Either[File, Project] }
object ProjectDependency
{
	final case class Execution(project: Either[File, Project]) extends ProjectDependency
	final case class Classpath(project: Either[File, Project], configuration: Option[String]) extends ProjectDependency
}
sealed trait ProjectDependencyConstructor {
	def %(conf: String): ProjectDependency.Classpath
}