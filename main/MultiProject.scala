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

object MultiProject
{
	val ExternalProjects = AttributeKey[Map[File, Project]]("external-projects")
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

	def load(configuration: AppConfiguration, log: Logger)(base: File): Project =
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
		val info = ProjectInfo(None, base, projectDir, Nil, None)(configuration, analysis, inputs, load(configuration, log))

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
	def externalProjects(p: Project) = p.aggregate._1 ++ p.dependencies._1.map(_.path)
	def internalProjects(p: Project) = p.aggregate._2 ++ p.dependencies._2.map(_.project)

	def internalTopologicalSort(root: Project): Seq[Project] =
		Dag.topologicalSort(root)(internalProjects)

	def topologicalSort(root: Project, state: State): Seq[Project] = topologicalSort(root)(externalMap(state))
	def topologicalSort(root: Project)(implicit resolveExternal: File => Project): Seq[Project] =
		Dag.topologicalSort(root) { p =>
			(externalProjects(p) map resolveExternal) ++ internalProjects(p)
		}
	def externalMap(state: State): File => Project = state get MultiProject.ExternalProjects getOrElse Map.empty

	def makeContext(root: Project, state: State) =
	{
		val allProjects = topologicalSort(root, state)
		val contexts = allProjects map { p => (p, ReflectiveContext(p, p.name)) }
		val externals = externalMap(state)
		def subs(f: Project => (Iterable[File], Iterable[Project])): Project =>  Iterable[Project] = p =>
		{
			val (ext, int) = f(p)
			(ext map externals) ++ int
		}
		val deps = (p: Project) => {
			val (dsI, dsE) = p.dependencies
			(dsI.map(_.path), dsE.map(_.project))
		}
		MultiContext(contexts)(subs(_.aggregate), subs(deps) )
	}
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

trait Project extends Tasked
{
	val info: ProjectInfo
	def name: String = info.name getOrElse "'name' not overridden"

	def base = info.projectDirectory
	def outputRootPath = base / "target"
	def streamBase = outputRootPath / "streams"

	implicit def streams = Dummy.Streams
	def input = Dummy.In
	def state = Dummy.State

	// (external, internal)
	def aggregate: (Iterable[File], Iterable[Project])
	def dependencies: (Iterable[ExternalDependency], Iterable[ProjectDependency])

	type Task[T] = sbt.Task[T]
	def act(input: Input, state: State): Option[(Task[State], Execute.NodeView[Task])] =
	{
		import Dummy._
		val context = MultiProject.makeContext(this, state)
		val dummies = new Transform.Dummies(In, State, Streams)
		def name(t: Task[_]): String = context.staticName(t) getOrElse std.Streams.name(t)
		val actualStreams = std.Streams(t => context.owner(t).get.streamBase / name(t), (t, writer) => ConsoleLogger() )
		val injected = new Transform.Injected( input, state, actualStreams )
		context.static(this, input.name) map { t => (t.merge.map(_ => state), Transform(dummies, injected, context) ) }
	}

	def help: Seq[Help] = Nil
}

trait ReflectiveProject extends Project
{
	import ReflectUtilities.allVals
	private[this] def vals[T: Manifest] = allVals[T](this).map(_._2)
	def aggregate: (Iterable[File], Iterable[Project]) = (vals[ExternalProject].map(_.path), vals[Project] )
	/** All projects directly contained in this that are defined in this container's compilation set.
	* This is for any contained projects, including execution and classpath dependencies, but not external projects.  */
	def dependencies: (Iterable[ExternalDependency], Iterable[ProjectDependency]) = (vals[ExternalDependency], vals[ProjectDependency])
}

trait ProjectConstructors
{
	val info: ProjectInfo
	def project(base: Path, name: String, deps: ProjectDependency*): Project
	def project[P <: Project](path: Path, name: String, builderClass: Class[P], deps: ProjectDependency*): P
	def project[P <: Project](path: Path, name: String, construct: ProjectInfo => P, deps: ProjectDependency*): P
	def project(base: Path): ExternalProject = new ExternalProject(base.asFile)

	implicit def defaultProjectDependency(p: Project): ProjectDependency = new ProjectDependency(p, None)
	implicit def dependencyConstructor(p: Project): ProjectDependencyConstructor = new ProjectDependencyConstructor {
		def %(conf: String) = new ProjectDependency(p, Some(conf))
	}
	implicit def extDependencyConstructor(p: ExternalProject): ExtProjectDependencyConstructor = new ExtProjectDependencyConstructor {
		def %(conf: String) = new ExternalDependency(p.path, Some(conf))
	}
}
final class ProjectDependency(val project: Project, val configuration: Option[String])
sealed trait ProjectDependencyConstructor {
	def %(conf: String): ProjectDependency
}
sealed trait ExtProjectDependencyConstructor {
	def %(conf: String): ExternalDependency
}
final class ExternalProject(val path: File)
final class ExternalDependency(val path: File, val configuration: Option[String])