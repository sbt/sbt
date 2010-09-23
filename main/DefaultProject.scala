/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt

	import std._
	import inc.Analysis
	import TaskExtra._
	import Configurations.{Compile => CompileConfig, Test => TestConfig}
	import ClasspathProject._
	import Types._

	import java.io.File

abstract class DefaultProject extends TestProject with MultiClasspathProject with ReflectiveClasspathProject
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
				case CompileConfig => analyzed(compile, compileInputs) named(name + "/analyzed") map { _ :: Nil } named(name + "/products")
				case x => error("Unknown compilation configuration: " + x)
			}
		}

	def sourceFilter: FileFilter = "*.java" | "*.scala"
	// temporary default for simpler demo
	override def configurations = super.configurations ++ Configurations.defaultMavenConfigurations

	def compileTask(inputs: Task[Compile.Inputs]): Task[Analysis] = inputs map Compile.apply

	def compileInputsTask(configuration: Configuration, base: PathFinder): Task[Compile.Inputs] =
	{
		val dep = dependencyClasspath(configuration)
		val prod: Task[Seq[Attributed[File]]] = (configuration.extendsConfigs map products).join.map(_.flatten)
		(dep, prod) map { case (cp :+: prodcp :+: HNil) =>
			val log = ConsoleLogger()
			val compilers = Compile.compilers(info.configuration, log)
			val javaSrc = base / "java"
			val scalaSrc = base / "scala"
			val out = "target" / compilers.scalac.scalaInstance.actualVersion
				import Path._
			val sources = descendents((javaSrc +++ scalaSrc), sourceFilter) +++ (if(configuration == CompileConfig) info.projectDirectory * (sourceFilter -- defaultExcludes) else Path.emptyPathFinder)
			val classes = classesDirectory(configuration)
			val classpath = (classes +: data(prodcp)) ++ data(cp)
			val analysis = analysisMap(prodcp ++ cp)
			Compile.inputs(classpath, sources.getFiles.toSeq, classes, scalacOptions, javacOptions, javaSrc.getFiles.toSeq, analysis, cacheDirectory, 100)(compilers, log)
		}
	}

	lazy val compileInputs: Task[Compile.Inputs] = compileInputsTask(Configurations.Compile,  "src" / "main") named(name + "/compile-inputs")
	lazy val compile: Task[Analysis] = compileTask(compileInputs) named(name + "/compile")
}
