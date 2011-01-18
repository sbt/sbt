/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt

	import std._
	import compile.{Discovered,Discovery}
	import inc.Analysis
	import TaskExtra._
	import Path.fileToPath
	import Configurations.{Compile => CompileConfig, Test => TestConfig, Runtime => RunConfig, Default => DefaultConfig, IntegrationTest => ITestConfig}
	import ClasspathProject._
	import Types._
	import xsbti.api.Definition

	import org.scalatools.testing.Framework
	import java.io.File

class DefaultProject(val info: ProjectInfo) extends BasicProject
{
	override def name = "test"
}
trait IntegrationTest extends BasicProject
{
	override def directoryProductsTask(conf: Configuration): Task[Seq[Attributed[File]]] =
		conf match {
			case ITestConfig => makeProducts(integrationTestCompile.compile, integrationTestCompile.compileInputs, name, "it-")
			case _ => super.directoryProductsTask(conf)
		}

	override def configurations: Seq[Configuration] = super.configurations :+ Configurations.IntegrationTest

	lazy val integrationTestOptions: Task[Seq[TestOption]] = testOptions
	lazy val integrationTest = testTasks(Some("it"), ITestConfig, integrationTestOptions, integrationTestCompile.compile, buildScalaInstance)
	lazy val integrationTestCompile = compileTasks(Some("it"), ITestConfig, "src" / "it", Path.emptyPathFinder, buildScalaInstance)
	lazy val integrationTestPackage = packages(ITestConfig)
}
abstract class BasicProject extends TestProject with MultiClasspathProject with ReflectiveClasspathProject
{
	// easier to demo for now
	override def organization = "org.example"
	override def version = "1.0"
	def artifactID = normalizedName
	override def artifacts: Seq[Artifact] = Artifact(artifactID) :: pomArtifact
	def pomArtifact = (if(publishMavenStyle) Artifact(artifactID, "pom", "pom") :: Nil else Nil)

	override def watchPaths: PathFinder = (info.projectDirectory: Path) * sourceFilter +++ descendents("src","*")

	def javacOptions: Seq[String] = Nil
	def scalacOptions: Seq[String] = Nil
	def consoleOptions: Seq[String] = scalacOptions
	def initialCommands: String = ""
	def maximumErrors: Int = 100

	def outputDirectory = "target": Path
	def cacheDirectory = outputDirectory / "cache"
	def mainResources = descendents("src" / "main" / "resources" ###, "*")
	def testResources = descendents("src" / "test" / "resources" ###, "*")

	def classesDirectory(configuration: Configuration): File =
		outputDirectory / (configString(configuration, "", "-") + "classes")

	def packageToPublish: Seq[Task[_]] = configurations map packages.apply

	lazy val products: Classpath = directoryProducts //TaskMap(productsTask)

	lazy val directoryProducts = TaskMap(directoryProductsTask)
	lazy val packages = TaskMap(packageTask)
	lazy val pkgMainClass = TaskMap(mainClassTask)
	lazy val jarPath = TaskMap(jarPathTask)
	lazy val javaps = TaskMap(javapCompiledTask)

	lazy val javap = javaps(CompileConfig)
	lazy val testJavap = javaps(TestConfig)

	lazy val `package` = packages(CompileConfig)
	lazy val testPackage = packages(TestConfig)

	def javapCompiledTask(conf: Configuration): Task[Unit] =
		javapTask(taskData(fullClasspath(conf)), buildScalaInstance)

	def directoryProductsTask(conf: Configuration): Task[Seq[Attributed[File]]] =
		conf match {
			case CompileConfig | DefaultConfig => makeProducts(compile.compile, compile.compileInputs, name, "")
			case TestConfig => makeProducts(testCompile.compile, testCompile.compileInputs, name, "test-")
			case x => task { Nil }
		}

	def mainClassesTask(conf: Configuration): Task[Seq[String]] = conf match {
		case CompileConfig => discoveredMainClasses
		case TestConfig => test.testDiscoveredMainClasses
		case _ => task { Nil }
	}
	def mainClassTask(conf: Configuration): Task[Option[String]]  =  mainClassesTask(conf) map { classes => SelectMainClass(None, classes) }
	def configString(conf: Configuration, pre: String, post: String): String = conf match {
		case CompileConfig | DefaultConfig => ""
		case _ => pre + conf.name + post
	}

	def jarPathTask(conf: Configuration): Task[File]  =  task { outputDirectory / jarName(conf) }
	def jarName(conf: Configuration): String  =  artifactID + "-" + version + configString(conf, "-", "") + ".jar"

	def packageConfigTask(conf: Configuration): Task[Package.Configuration] =
		pkgMainClass(conf) :^: directoryProductsTask(conf) :^: jarPath(conf) :^: KNil map { case main :+: in :+: jar :+: HNil =>
			val srcs = data(in) flatMap { dir => descendents(dir ###, "*").xx }
			new Package.Configuration(srcs, jar, main.map(Package.MainClass.apply).toList)
		}

	def packageTask(conf: Configuration): Task[Seq[Attributed[File]]] =
		streams :^: packageConfigTask(conf) :^: KNil map { case s :+: config :+: HNil =>
			Package(config, cacheDirectory / conf.name / "package", s.log)
			List(config.jar)
		}

	lazy val buildScalaVersions: Task[String] = task { info.app.scalaProvider.version }//cross(MultiProject.ScalaVersion)(info.app.scalaProvider.version)
	lazy val buildScalaInstance: Task[ScalaInstance] =
		buildScalaVersions map { version => ScalaInstance(version, info.app.scalaProvider) }

	lazy val discoverMain: Task[Seq[(Definition,Discovered)]] =
		compile.compile map { analysis => Discovery.applications(Test.allDefs(analysis)) }

	lazy val discoveredMainClasses: Task[Seq[String]] =
		discoverMain map { _ collect { case (definition, discovered) if(discovered.hasMain) => definition.name } }

	lazy val runner: Task[ScalaRun] =
		 buildScalaInstance map { si => new Run(si) }

	lazy val run = runTasks(None, RunConfig, discoveredMainClasses)
	lazy val testRun = runTasks(Some("test"), TestConfig, test.testDiscoveredMainClasses)

	lazy val testFrameworks: Task[Seq[TestFramework]] = task {
		import TestFrameworks._
		Seq(ScalaCheck, Specs, ScalaTest, ScalaCheckCompat, ScalaTestCompat, SpecsCompat, JUnit)
	}

	lazy val testOptions: Task[Seq[TestOption]] = task { Nil }

	lazy val test = testTasks(None, TestConfig, testOptions, testCompile.compile, buildScalaInstance)

	lazy val compile = compileTasks(None, CompileConfig, "src" / "main", info.projectDirectory : Path, buildScalaInstance)
	lazy val testCompile = compileTasks(Some("test"), TestConfig, "src" / "test", Path.emptyPathFinder, buildScalaInstance)

	lazy val consoleQuick = consoleTask(dependencyClasspath(CompileConfig), consoleOptions, initialCommands, compile.compileInputs)
	lazy val console = consoleTask(CompileConfig, consoleOptions, initialCommands, compile.compileInputs)
	lazy val testConsole = consoleTask(TestConfig, consoleOptions, initialCommands, testCompile.compileInputs)

	lazy val doc = docTask("main", compile.compileInputs)
	lazy val testDoc = docTask("test", testCompile.compileInputs)

	lazy val clean = task { IO.delete(outputDirectory) }

	// lazy val test-only, test-quick, test-failed, package-src, package-doc, jetty-{run,stop,restart}, prepare-webapp

	def sourceFilter: FileFilter = "*.java" | "*.scala"

	def compileTask(inputs: Task[Compile.Inputs]): Task[Analysis] =
		inputs :^: streams :^: KNil map { case i :+: s :+: HNil => Compile(i, s.log) }

	def compileInputsTask(configuration: Configuration, bases: PathFinder, shallow: PathFinder, scalaInstance: Task[ScalaInstance]): Task[Compile.Inputs] =
	{
		val dep = dependencyClasspath(configuration)
		(dep, scalaInstance) map { case (cp :+: si :+: HNil) =>
			val log = ConsoleLogger()
			val compilers = Compile.compilers(si)(info.configuration, log)
			val javaSrc = base / "java"
			val scalaSrc = base / "scala"
			val out = "target" / si.actualVersion
				import Path._
			val deepBases = (bases / "java") +++ (bases / "scala")
			val allBases = deepBases +++ shallow
			val sources = descendents(deepBases, sourceFilter) +++ shallow * (sourceFilter -- defaultExcludes)
			val classes = classesDirectory(configuration)
			val classpath = classes +: data(cp)
			val analysis = analysisMap(cp)
			val cache = cacheDirectory / "compile" / configuration.toString
			Compile.inputs(classpath, sources.getFiles.toSeq, classes, scalacOptions, javacOptions, allBases.getFiles.toSeq, analysis, cache, maximumErrors)(compilers, log)
		}
	}

	def copyResourcesTask(resources: PathFinder, config: Configuration): Task[Relation[File,File]] =
		streams map { s =>
			val target = classesDirectory(config)
			val cacheFile = cacheDirectory / config.name / "copy-resources"
			val mappings = resources.get.map(path => (path.asFile, new File(target, path.relativePath)))
			s.log.debug("Copy resource (" + config.name + ") mappings: " + mappings.mkString("\n\t"))
			Sync(cacheFile)( mappings )
		}

	lazy val copyResources = copyResourcesTask(mainResources, CompileConfig)
	lazy val copyTestResources = copyResourcesTask(testResources, TestConfig)
	
	def syncTask(cacheFile: File, mappings: Iterable[(File, File)]): Task[Relation[File,File]] =
		task { Sync(cacheFile)(mappings) }

	def consoleTask(config: Configuration, options: Seq[String], initialCommands: String, compilers: Task[Compile.Inputs]): Task[Unit] =
		consoleTask(fullClasspath(config), options, initialCommands, compilers)
	def consoleTask(classpath: Task[Seq[Attributed[File]]], options: Seq[String], initialCommands: String, compilers: Task[Compile.Inputs]): Task[Unit] =
		consoleTask(compilers.map(_.compilers), classpath, options, initialCommands)
	def consoleTask(compilers: Task[Compile.Compilers], classpath: Task[Seq[Attributed[File]]], options: Seq[String], initialCommands: String): Task[Unit] =
		compilers :^: classpath :^: streams :^: KNil map { case cs :+: cp :+: s :+: HNil =>
			(new Console(cs.scalac))(data(cp), options, initialCommands, s.log)
		}

	def compileTasks(prefix: Option[String], config: Configuration, base: PathFinder, shallow: PathFinder, si: Task[ScalaInstance]): CompileTasks =
	{
		val confStr = if(config == Configurations.Compile) "" else config.name + "-"
		val compileInputs: Task[Compile.Inputs] = compileInputsTask(config, base, shallow, buildScalaInstance) named(confStr + "compile-inputs")
		val compile: Task[Analysis] = compileTask(compileInputs) named(confStr + "compile")
		new CompileTasks(prefix, compile, compileInputs)
	}

	def testTasks(prefix: Option[String], config: Configuration, options: Task[Seq[TestOption]], compile: Task[Analysis], instance: Task[ScalaInstance]): TestTasks =
		new TestTasks(prefix, this, testFrameworks, config, options, compile, instance)

	def runTasks(prefix: Option[String], config: Configuration, discoveredMainClasses: Task[Seq[String]]): RunTasks =
		new RunTasks(prefix, this, config, discoveredMainClasses, runner)

	def docTask(label: String, inputs: Task[Compile.Inputs]): Task[Unit] =
		inputs :^: streams :^: KNil map { case in :+: s :+: HNil =>
			val d = new Scaladoc(maximumErrors, in.compilers.scalac)
			d(label, in.config.sources, in.config.classpath, outputDirectory / "doc", scalacOptions)(s.log)
		}
}

// TODO: move these to separate file.  The main problem with this approach is modifying dependencies and otherwise overriding a task.
//  Intend to remedy this with injections instead of overrides
final class CompileTasks(val prefix: Option[String], val compile: Task[Analysis], val compileInputs: Task[Compile.Inputs]) extends TaskGroup

class RunTasks(val prefix: Option[String], val project: ClasspathProject with Project, val config: Configuration, discoveredMainClasses: Task[Seq[String]], runner: Task[ScalaRun]) extends TaskGroup
{
	def selectRunMain(allMainClasses: Task[Seq[String]]): Task[Option[String]] =
		allMainClasses map { classes => SelectMainClass(Some(SimpleReader readLine _), classes) }

	def runTask(fullcp: Task[Seq[Attributed[File]]], mainClass: Task[Option[String]]): Task[Unit] =
		runTask(project.input, fullcp, mainClass, project.streams, runner)

	def runTask(input: Task[Input], fullcp: Task[Seq[Attributed[File]]], mainClass: Task[Option[String]], streams: Task[TaskStreams], runner: Task[ScalaRun]): Task[Unit] =
		(input :^: fullcp :^: mainClass :^: streams :^: runner :^: KNil) map { case in :+: cp :+: main :+: s :+: r :+: HNil => run0(in.splitArgs, cp, main, s.log, r) }

	def run0(args: Seq[String], cp: Seq[Attributed[File]], main: Option[String], log: Logger, r: ScalaRun)
	{
		val mainClass = main getOrElse error("No main class detected.")
		val classpath = cp.map(x => Path.fromFile(x.data))
		r.run(mainClass, classpath, args, log) foreach error
	}

	lazy val runMainClass: Task[Option[String]] = selectRunMain(discoveredMainClasses)
	lazy val run = runTask(project.fullClasspath(config), runMainClass)
}

class TestTasks(val prefix: Option[String], val project: ClasspathProject with Project, testFrameworks: Task[Seq[TestFramework]], val config: Configuration, options: Task[Seq[TestOption]], compile: Task[Analysis], scalaInstance: Task[ScalaInstance]) extends TaskGroup
{
	lazy val testLoader: Task[ClassLoader] =
		project.fullClasspath(config) :^: scalaInstance :^: KNil map { case classpath :+: instance :+: HNil =>
			TestFramework.createTestLoader(data(classpath), instance)
		}
	
	lazy val loadedTestFrameworks: Task[Map[TestFramework,Framework]] =
		testFrameworks :^: project.streams :^: testLoader :^: KNil map { case frameworks :+: s :+: loader :+: HNil =>
			frameworks.flatMap( f => f.create(loader, s.log).map( x => (f, x)).toIterable ).toMap
		}
		
	lazy val discoverTest: Task[(Seq[TestDefinition], Set[String])] =
		loadedTestFrameworks :^: compile :^: KNil map { case frameworkMap :+: analysis :+: HNil =>
			Test.discover(frameworkMap.values.toSeq, analysis)
		}
	lazy val definedTests: Task[Seq[TestDefinition]] = discoverTest.map(_._1)
	lazy val testDiscoveredMainClasses: Task[Seq[String]] = discoverTest.map(_._2.toSeq)

	lazy val executeTests = (loadedTestFrameworks :^: options :^: testLoader :^: definedTests :^: project.streams :^: KNil) flatMap {
		case frameworkMap :+: options :+: loader :+: discovered :+: s :+: HNil =>

		Test(frameworkMap, loader, discovered, options, s.log)
	}
	lazy val test = (project.streams, executeTests) map { case s :+: results :+: HNil => Test.showResults(s.log, results) }
}