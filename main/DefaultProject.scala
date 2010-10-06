/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt

	import std._
	import compile.{Discovered,Discovery}
	import inc.Analysis
	import TaskExtra._
	import Configurations.{Compile => CompileConfig, Test => TestConfig, Runtime => RunConfig}
	import ClasspathProject._
	import Types._
	import xsbti.api.Definition

	import java.io.File

class DefaultProject(val info: ProjectInfo) extends BasicProject
{
	override def name = "test"
}
abstract class BasicProject extends TestProject with MultiClasspathProject with ReflectiveClasspathProject
{
	// easier to demo for now
	override def organization = "org.example"
	override def version = "1.0"

	override def watchPaths: PathFinder = descendents("src","*")

	def javacOptions: Seq[String] = Nil
	def scalacOptions: Seq[String] = Nil

	def outputDirectory = "target": Path
	def cacheDirectory = outputDirectory / "cache"

	def classesDirectory(configuration: Configuration): File =
		configuration match {
			case CompileConfig => outputDirectory / "classes"
			case x => outputDirectory / (x.toString + "-classes")
		}

	 // TODO: resources, jars, test classes
	lazy val products: Classpath =
		TaskMap { (conf: Configuration) =>
			conf match {
				case CompileConfig | RunConfig => analyzed(compile, compileInputs) named(name + "/analyzed") map { _ :: Nil } named(name + "/products")
				case x => error("Unknown compilation configuration: " + x)
			}
		}

	lazy val buildScalaInstance: Task[ScalaInstance] = task {
		val provider = info.app.scalaProvider
		ScalaInstance(provider.version, provider)
	}

	lazy val discover: Task[Seq[(Definition,Discovered)]] =
		compile map { analysis => Discovery(Set.empty, Set.empty)(analysis.apis.internal.values.flatMap(_.definitions).toSeq) }

	lazy val discoveredMainClasses: Task[Seq[String]] =
		discover map { _ collect { case (definition, discovered) if(discovered.hasMain) => definition.name } }

	lazy val runMainClass: Task[Option[String]] =
		 discoveredMainClasses map { classes => SelectMainClass(Some(SimpleReader readLine _), classes) }

	lazy val pkgMainClass: Task[Option[String]] =
		 discoveredMainClasses map { classes => SelectMainClass(None, classes) }

	lazy val runner: Task[ScalaRun] =
		 buildScalaInstance map { si => new Run(si) }

	lazy val run = (input :^: fullClasspath(RunConfig) :^: runMainClass :^: streams :^: runner :^: KNil) map { case in :+: cp :+: main :+: s :+: r :+: HNil =>	
		val mainClass = main getOrElse error("No main class detected.")
		val classpath = cp.map(x => Path.fromFile(x.data))
		r.run(mainClass, classpath, in.splitArgs, s.log) foreach error
	}
	lazy val clean = task {
		IO.delete(outputDirectory)
	}
	lazy val set = input map { in =>
		val Seq(name, value) = in.splitArgs.take(2)
		println(name + "=" + value)
		java.lang.System.setProperty(name, value)
	}

	def sourceFilter: FileFilter = "*.java" | "*.scala"

	def compileTask(inputs: Task[Compile.Inputs]): Task[Analysis] = inputs map Compile.apply

	def compileInputsTask(configuration: Configuration, base: PathFinder, scalaInstance: Task[ScalaInstance]): Task[Compile.Inputs] =
	{
		val dep = dependencyClasspath(configuration)
		val prod: Task[Seq[Attributed[File]]] = (configuration.extendsConfigs map products).join.map(_.flatten)
		(dep, prod, scalaInstance) map { case (cp :+: prodcp :+: si :+: HNil) =>
			val log = ConsoleLogger()
			val compilers = Compile.compilers(si)(info.configuration, log)
			val javaSrc = base / "java"
			val scalaSrc = base / "scala"
			val out = "target" / si.actualVersion
				import Path._
			val sources = descendents((javaSrc +++ scalaSrc), sourceFilter) +++ (if(configuration == CompileConfig) info.projectDirectory * (sourceFilter -- defaultExcludes) else Path.emptyPathFinder)
			val classes = classesDirectory(configuration)
			val classpath = (classes +: data(prodcp)) ++ data(cp)
			val analysis = analysisMap(prodcp ++ cp)
			Compile.inputs(classpath, sources.getFiles.toSeq, classes, scalacOptions, javacOptions, javaSrc.getFiles.toSeq, analysis, cacheDirectory, 100)(compilers, log)
		}
	}

	lazy val compileInputs: Task[Compile.Inputs] = compileInputsTask(Configurations.Compile,  "src" / "main", buildScalaInstance) named(name + "/compile-inputs")
	lazy val compile: Task[Analysis] = compileTask(compileInputs) named(name + "/compile")
}
