/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import std._
import Path._
import TaskExtra._
import GlobFilter._
import inc.Analysis
import build.{Auto, Build}
import xsbti.AppConfiguration

import java.io.File

object MultiProject
{
	val ExternalProjects = AttributeKey[Map[File, Project]]("external-projects")

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

	def loadExternals[P](from: Iterable[ProjectContainer], loadImpl: File => P): Map[File, P] =
	{
		def load(loaded: Map[File, P], file: File): Map[File, P] =
			(loaded get file) match
			{
				case None => doLoad(loaded, file)
				case Some(p) => loaded
			}
		def doLoad(loaded: Map[File, P], file: File): Map[File, P] =
		{
			val loadedProject = loadImpl(file)
			val newMap = loaded.updated(file, loadedProject)
			loadedProject match
			{
				case container: ProjectContainer => loadAll( externals(container :: Nil), loaded )
				case _ => newMap
			}
		}
		def loadAll(files: Set[File], loaded: Map[File, P]): Map[File, P] = (loaded /: files)(load)

		loadAll(  externals(from) , Map.empty)
	}

	def externals(containers: Iterable[ProjectContainer]): Set[File] =
	{
		def exts(containers: Iterable[ProjectContainer]): Iterable[File] =
			containers flatMap { container => container.externalProjects ++ exts(container.internalProjects) }
		exts(containers).toSet
	}

	def makeContext(root: Project, state: State) =
	{
		val allProjects = ProjectContainer.topologicalSort(root, state)
		val names = allProjects.map { p: Project => (p, p.name) }.toMap
		//val tasks = allProjects map { p => 
		ReflectiveContext(root, names.get _)
	}
}

/*trait MultiContext[Owner] extends Context[Owner]
{
	def ownerForName(name: String): Option[Owner]
	def allTasks(owner: Owner): Iterable[Task[_]]
	def externalProject(base: File): Project
}*/

trait ProjectContainer
{
	/** All projects that are loaded from their base directory instead of being defined in this container's compilation set.
	* This is for any contained projects, including execution and classpath dependencies.  */
	def externalProjects: Iterable[File]
	/** All projects directly contained in this that are defined in this container's compilation set.
	* This is for any contained projects, including execution and classpath dependencies, but not external projects.  */
	def internalProjects: Iterable[Project]
}

object ProjectContainer
{
	def internalTopologicalSort(root: Project): Seq[Project] =
		Dag.topologicalSort(root) { _.internalProjects }

	def topologicalSort(root: Project, state: State): Seq[Project] = topologicalSort(root)(state get MultiProject.ExternalProjects getOrElse Map.empty)
	def topologicalSort(root: Project)(implicit resolveExternal: File => Project): Seq[Project] =
		Dag.topologicalSort(root) { p =>
			(p.externalProjects map resolveExternal) ++ p.internalProjects
		}
}

trait Project extends Tasked with ProjectContainer
{
	val info: ProjectInfo
	def name: String = info.name.get

	def base = info.projectDirectory
	def outputRootPath = base / "target"
	def streamBase = outputRootPath / "streams"

	implicit def streams = Dummy.Streams
	def input = Dummy.In
	def state = Dummy.State

	type Task[T] = sbt.Task[T]
	def act(input: Input, state: State): Option[(Task[State], Execute.NodeView[Task])] =
	{
		import Dummy._
		val context = MultiProject.makeContext(this, state)
		val dummies = new Transform.Dummies(In, State, Streams)
		def name(t: Task[_]): String = context.staticName(t) getOrElse std.Streams.name(t)
		val injected = new Transform.Injected( input, state, std.Streams(t => streamBase / name(t), (t, writer) => ConsoleLogger() ) )
		context.forName(input.name) map { t => (t.merge.map(_ => state), Transform(dummies, injected, context) ) }
	}

	def help: Seq[Help] = Nil
}

trait ProjectConstructors
{
	val info: ProjectInfo
	def project(base: Path, name: String, deps: ProjectDependency*): Project
	def project[P <: Project](path: Path, name: String, builderClass: Class[P], deps: ProjectDependency*): P
	def project[P <: Project](path: Path, name: String, construct: ProjectInfo => P, deps: ProjectDependency*): P
	def project(base: Path): ExternalProject = new ExternalProject(base)

	implicit def defaultProjectDependency(p: Project): ProjectDependency = new ProjectDependency(p, None)
	implicit def dependencyConstructor(p: Project): ProjectDependencyConstructor = new ProjectDependencyConstructor {
		def %(conf: String) = new ProjectDependency(p, Some(conf))
	}
}
final class ProjectDependency(val project: Project, val configuration: Option[String])
sealed trait ProjectDependencyConstructor {
	def %(conf: String): ProjectDependency
}
final class ExternalProject(val path: Path)