/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah, David MacIver
 */
package sbt

/** The default project when no project is explicitly configured and the common base class for
* configuring a project.*/
class DefaultProject(val info: ProjectInfo) extends BasicScalaProject with MavenStyleScalaPaths
class DefaultWebProject(val info: ProjectInfo) extends BasicWebScalaProject with MavenStyleWebScalaPaths


import BasicScalaProject._
import ScalaProject.{optionsAsString, javaOptionsAsString}
import java.io.File
import java.util.jar.Attributes

/** This class defines concrete instances of actions from ScalaProject using overridable paths,
* options, and configuration. */
abstract class BasicScalaProject extends ScalaProject with BasicDependencyProject with ScalaPaths
{
	/** The explicitly specified class to be run by the 'run' action.
	* See http://code.google.com/p/simple-build-tool/wiki/RunningProjectCode for details.*/
	def mainClass: Option[String] = None
	/** Gets the main class to use.  This is used by package and run to determine which main
	* class to run or include as the Main-Class attribute.
	* If `mainClass` is explicitly specified, it is used.  Otherwise, the main class is selected from
	* the classes with a main method as automatically detected by the analyzer plugin.
	* `promptIfMultipleChoices` controls the behavior when multiple main classes are detected.
	* If true, it prompts the user to select which main class to use.  If false, it prints a warning
	* and returns no main class.*/
	def getMainClass(promptIfMultipleChoices: Boolean) =
		mainClass orElse
		{
			val applications = mainCompileConditional.analysis.allApplications.toList
			impl.SelectMainClass(promptIfMultipleChoices, applications) orElse
			{
				if(!promptIfMultipleChoices && !applications.isEmpty)
					warnMultipleMainClasses(log)
				None
			}
		}
	/** Specifies the value of the `Class-Path` attribute in the manifest of the main jar. */
	def manifestClassPath: Option[String] = None
	def dependencies = info.dependencies ++ subProjects.values.toList

	val mainCompileConditional = new CompileConditional(mainCompileConfiguration)
	val testCompileConditional = new CompileConditional(testCompileConfiguration)
	
	def compileOrder = CompileOrder.Mixed

	/** The main artifact produced by this project. To redefine the main artifact, override `defaultMainArtifact`
	* Additional artifacts are defined by `val`s of type `Artifact`.*/
	lazy val mainArtifact = defaultMainArtifact
	/** Defines the default main Artifact assigned to `mainArtifact`.  By default, this is a jar file with name given
	* by `artifactID`.*/
	protected def defaultMainArtifact = Artifact(artifactID, "jar", "jar")
	
	import Project._
	
	/** The options provided to the 'compile' action to pass to the Scala compiler.*/
	def compileOptions: Seq[CompileOption] = Deprecation :: Nil
	/** The options provided to the 'compile' action to pass to the Java compiler. */
	def javaCompileOptions: Seq[JavaCompileOption] = Nil
	/** The options provided to the 'test-compile' action, defaulting to those for the 'compile' action.*/
	def testCompileOptions: Seq[CompileOption] = compileOptions
	/** The options provided to the 'test-compile' action to pass to the Java compiler. */
	def testJavaCompileOptions: Seq[JavaCompileOption] = javaCompileOptions
	
	/** The options provided to the 'doc' and 'docTest' actions.*/
	def documentOptions: Seq[ScaladocOption] =
		LinkSource ::
		documentTitle(name + " " + version + " API") ::
		windowTitle(name + " " + version + " API") ::
		Nil
	/** The options provided to the 'test' action..*/
	def testOptions: Seq[TestOption] =
		TestListeners(testListeners) ::
		TestFilter(includeTest) ::
		Nil
	/** The options provided to the clean action.  You can add files to be removed and files to be preserved here.*/
	def cleanOptions: Seq[CleanOption] =
		ClearAnalysis(mainCompileConditional.analysis) ::
		ClearAnalysis(testCompileConditional.analysis) ::
		historyPath.map(history => Preserve(history)).toList
		
	def packageOptions: Seq[PackageOption] =
		manifestClassPath.map(cp => ManifestAttributes( (Attributes.Name.CLASS_PATH, cp) )).toList :::
		getMainClass(false).map(MainClass(_)).toList
	
	private def succeededTestPath = testAnalysisPath / "succeeded-tests"
	private def quickOptions(failedOnly: Boolean) =
	{
		val path = succeededTestPath
		val analysis = testCompileConditional.analysis
		TestFilter(new impl.TestQuickFilter(analysis, failedOnly, path, log))  :: TestListeners(new impl.TestStatusReporter(path, log) :: Nil) :: Nil
	}
	
	protected def includeTest(test: String): Boolean = true

	/** This is called to create the initial directories when a user makes a new project from
	* sbt.*/
	override final def initializeDirectories()
	{
		FileUtilities.createDirectories(directoriesToCreate.map(_.asFile), log) match
		{
			case Some(errorMessage) => log.error("Could not initialize directory structure: " + errorMessage)
			case None => log.success("Successfully initialized directory structure.")
		}
	}
	import Configurations._
	/** The managed configuration to use when determining the classpath for a Scala interpreter session.*/
	def consoleConfiguration = Test
	
	/** A PathFinder that provides the classpath to pass to scaladoc.  It is the same as the compile classpath
	* by default. */
	def docClasspath = compileClasspath
	/** A PathFinder that provides the classpath to pass to the compiler.*/
	def compileClasspath = fullClasspath(Compile) +++ optionalClasspath
	/** A PathFinder that provides the classpath to use when unit testing.*/
	def testClasspath = fullClasspath(Test) +++ optionalClasspath
	/** A PathFinder that provides the classpath to use when running the class specified by 'getMainClass'.*/
	def runClasspath = fullClasspath(Runtime) +++ optionalClasspath
	/** A PathFinder that provides the classpath to use for a Scala interpreter session.*/
	def consoleClasspath = fullClasspath(consoleConfiguration) +++ optionalClasspath
	/** A PathFinder that corresponds to Maven's optional scope.  It includes any managed libraries in the
	* 'optional' configuration for this project only.*/
	def optionalClasspath = managedClasspath(Optional)
	/** A PathFinder that contains the jars that should be included in a comprehensive package.  This is
	* by default the 'runtime' classpath excluding the 'provided' classpath.*/
	def publicClasspath = runClasspath --- fullClasspath(Provided)

	/** This returns the unmanaged classpath for only this project for the given configuration.  It by
	* default includes the main compiled classes for this project and the libraries in this project's
	* unmanaged library directory (lib) and the managed directory for the specified configuration.  It
	* also adds the resource directories appropriate to the configuration.
	* The Provided and Optional configurations are treated specially; they are empty
	* by default.*/
	def fullUnmanagedClasspath(config: Configuration) =
	{
		config match
		{
			case CompilerPlugin => unmanagedClasspath
			case Runtime => runUnmanagedClasspath
			case Test => testUnmanagedClasspath
			case Provided | Optional => Path.emptyPathFinder
			case _ => mainUnmanagedClasspath
		}
	}
	/** The unmanaged base classpath.  By default, the unmanaged classpaths for test and run include this classpath. */
	protected def mainUnmanagedClasspath = mainCompilePath +++ mainResourceClasspath +++ unmanagedClasspath
	/** The unmanaged classpath for the run configuration. By default, it includes the base classpath returned by
	* `mainUnmanagedClasspath`.*/
	protected def runUnmanagedClasspath = mainUnmanagedClasspath +++ mainDependencies.scalaCompiler
	/** The unmanaged classpath for the test configuration.  By default, it includes the run classpath, which includes the base
	* classpath returned by `mainUnmanagedClasspath`.*/
	protected def testUnmanagedClasspath = testCompilePath +++ testResourceClasspath  +++ testDependencies.scalaCompiler +++ runUnmanagedClasspath
	
	/** @deprecated Use `mainDependencies.scalaJars`*/
	@deprecated protected final def scalaJars: Iterable[File] = mainDependencies.scalaJars.get.map(_.asFile)
	/** An analysis of the jar dependencies of the main Scala sources.  It is only valid after main source compilation.
	* See the LibraryDependencies class for details. */
	final def mainDependencies = new LibraryDependencies(this, mainCompileConditional)
	/** An analysis of the jar dependencies of the test Scala sources.  It is only valid after test source compilation.
	* See the LibraryDependencies class for details. */
	final def testDependencies = new LibraryDependencies(this, testCompileConditional)

	/** The list of test frameworks to use for testing.  Note that adding frameworks to this list
	* for an active project currently requires an explicit 'clean' to properly update the set of tests to
	* run*/
	def testFrameworks: Iterable[TestFramework] = ScalaCheckFramework :: SpecsFramework :: ScalaTestFramework :: Nil
	/** The list of listeners for testing. */
	def testListeners: Seq[TestReportListener] = new LogTestReportListener(log) :: Nil
	
	def mainLabel = "main"
	def testLabel = "test"
	
	def mainCompileConfiguration = new MainCompileConfig
	def testCompileConfiguration = new TestCompileConfig
	abstract class BaseCompileConfig extends CompileConfiguration
	{
		def log = BasicScalaProject.this.log
		def projectPath = info.projectPath
		def baseCompileOptions: Seq[CompileOption]
		lazy val localBaseOptions = baseCompileOptions
		def options = optionsAsString(localBaseOptions.filter(!_.isInstanceOf[MaxCompileErrors]))
		def maxErrors = maximumErrors(localBaseOptions)
		def compileOrder = BasicScalaProject.this.compileOrder
	}
	class MainCompileConfig extends BaseCompileConfig
	{
		def baseCompileOptions = compileOptions
		def label = mainLabel
		def sourceRoots = mainSourceRoots
		def sources = mainSources
		def outputDirectory = mainCompilePath
		def classpath = compileClasspath
		def analysisPath = mainAnalysisPath
		def testDefinitionClassNames = Nil
		def javaOptions = javaOptionsAsString(javaCompileOptions)
	}
	class TestCompileConfig extends BaseCompileConfig
	{
		def baseCompileOptions = testCompileOptions
		def label = testLabel
		def sourceRoots = testSourceRoots
		def sources = testSources
		def outputDirectory = testCompilePath
		def classpath = testClasspath
		def analysisPath = testAnalysisPath
		def testDefinitionClassNames = testFrameworks.map(_.testSuperClassName)
		def javaOptions = javaOptionsAsString(testJavaCompileOptions)
	}
	
	/** Configures forking the compiler and runner.  Use ForkScalaCompiler, ForkScalaRun or mix together.*/
	def fork: Option[ForkScala] = None
	private def doCompile(conditional: CompileConditional) =
	{
		fork match
		{
			case Some(fc: ForkScalaCompiler) => ForkCompile(fc, conditional)
			case _ => conditional.run
		}
	}
	private def getRunner =
	{
		fork match
		{
			case Some(fr: ForkScalaRun) => new ForkRun(fr)
			case _ => Run
		}
	}
	
	protected def compileAction = task { doCompile(mainCompileConditional) } describedAs MainCompileDescription
	protected def testCompileAction = task { doCompile(testCompileConditional) } dependsOn compile describedAs TestCompileDescription
	protected def cleanAction = cleanTask(outputPath, cleanOptions) describedAs CleanDescription
	protected def runAction = task { args => runTask(getMainClass(true), runClasspath, args, getRunner) dependsOn(compile) } describedAs RunDescription
	protected def consoleQuickAction = consoleTask(consoleClasspath, getRunner) describedAs ConsoleQuickDescription
	protected def consoleAction = consoleTask(consoleClasspath, getRunner).dependsOn(testCompile) describedAs ConsoleDescription
	protected def docAction = scaladocTask(mainLabel, mainSources, mainDocPath, docClasspath, documentOptions).dependsOn(compile) describedAs DocDescription
	protected def docTestAction = scaladocTask(testLabel, testSources, testDocPath, docClasspath, documentOptions).dependsOn(testCompile) describedAs TestDocDescription
	protected def testAction = defaultTestTask(testOptions)
	protected def testOnlyAction = testQuickMethod(testCompileConditional.analysis, testOptions)(options =>
		defaultTestTask(options)) describedAs(TestOnlyDescription)
	protected def testQuickAction = defaultTestQuickMethod(false) describedAs(TestQuickDescription)
	protected def testFailedAction = defaultTestQuickMethod(true) describedAs(TestFailedDescription)
	protected def defaultTestQuickMethod(failedOnly: Boolean) =
		testQuickMethod(testCompileConditional.analysis, testOptions)(options => defaultTestTask(quickOptions(failedOnly) ::: options.toList))
	protected def defaultTestTask(testOptions: => Seq[TestOption]) =
		testTask(testFrameworks, testClasspath, testCompileConditional.analysis, testOptions).dependsOn(testCompile) describedAs TestDescription
		
	override def packageToPublishActions: Seq[ManagedTask] = `package` :: Nil
	
	protected def packageAction = packageTask(packagePaths, jarPath, packageOptions).dependsOn(compile) describedAs PackageDescription
	protected def packageTestAction = packageTask(packageTestPaths, packageTestJar).dependsOn(testCompile) describedAs TestPackageDescription
	protected def packageDocsAction = packageTask(mainDocPath ##, packageDocsJar, Recursive).dependsOn(doc) describedAs DocPackageDescription
	protected def packageSrcAction = packageTask(packageSourcePaths, packageSrcJar) describedAs SourcePackageDescription
	protected def packageTestSrcAction = packageTask(packageTestSourcePaths, packageTestSrcJar) describedAs TestSourcePackageDescription
	protected def packageProjectAction = zipTask(packageProjectPaths, packageProjectZip) describedAs ProjectPackageDescription
	
	protected def docAllAction = (doc && docTest) describedAs DocAllDescription
	protected def packageAllAction = task { None } dependsOn(`package`, packageTest, packageSrc, packageTestSrc, packageDocs) describedAs PackageAllDescription
	protected def graphAction = graphTask(graphPath, mainCompileConditional.analysis).dependsOn(compile)
	protected def incrementVersionAction = task { incrementVersionNumber(); None } describedAs IncrementVersionDescription
	protected def releaseAction = (test && packageAll && incrementVersion) describedAs ReleaseDescription
	
	lazy val compile = compileAction
	lazy val testCompile = testCompileAction
	lazy val clean = cleanAction
	lazy val run = runAction
	lazy val consoleQuick = consoleQuickAction
	lazy val console = consoleAction
	lazy val doc = docAction
	lazy val docTest = docTestAction
	lazy val test = testAction
	lazy val `package` = packageAction
	lazy val packageTest = packageTestAction
	lazy val packageDocs = packageDocsAction
	lazy val packageSrc = packageSrcAction
	lazy val packageTestSrc = packageTestSrcAction
	lazy val packageProject = packageProjectAction
	lazy val docAll = docAllAction
	lazy val packageAll = packageAllAction
	lazy val graph = graphAction
	lazy val incrementVersion = incrementVersionAction
	lazy val release = releaseAction

	lazy val testQuick = testQuickAction
	lazy val testFailed = testFailedAction
	lazy val testOnly = testOnlyAction
	
	def jarsOfProjectDependencies = Path.lazyPathFinder {
		topologicalSort.dropRight(1) flatMap { p =>
			p match
			{
				case bpp: BasicScalaPaths => List(bpp.jarPath)
				case _ => Nil
			}
		}
	}
	override def deliverScalaDependencies: Iterable[ModuleID] =
	{
		val snapshot = mainDependencies.snapshot
		mapScalaModule(snapshot.scalaLibrary, ScalaArtifacts.LibraryID) ++
		mapScalaModule(snapshot.scalaCompiler, ScalaArtifacts.CompilerID)
	}
	override def watchPaths = mainSources +++ testSources +++ mainResources +++ testResources
}
abstract class BasicWebScalaProject extends BasicScalaProject with WebScalaProject with WebScalaPaths
{
	import BasicWebScalaProject._
	override def watchPaths = super.watchPaths +++ webappResources
	
	lazy val prepareWebapp = prepareWebappAction
	protected def prepareWebappAction =
		prepareWebappTask(webappResources, temporaryWarPath, webappClasspath, mainDependencies.scalaJars) dependsOn(compile)
	
	def webappClasspath = publicClasspath
	def jettyRunClasspath = testClasspath
	def jettyWebappPath = temporaryWarPath
	lazy val jettyRun = jettyRunAction
	lazy val jetty = task { idle() } dependsOn(jettyRun) describedAs(JettyDescription)
	protected def jettyRunAction =
		jettyRunTask(jettyWebappPath, jettyContextPath, jettyPort, jettyRunClasspath, "test", scanDirectories.map(_.asFile), scanInterval) dependsOn(prepareWebapp) describedAs(JettyRunDescription)
	private def idle() =
	{
		log.info("Waiting... (press any key to interrupt)")
		def doWait()
		{
			try { Thread.sleep(1000) } catch { case _: InterruptedException => () }
			if(System.in.available() <= 0)
				doWait()
		}
		doWait()
		while (System.in.available() > 0) System.in.read()
		None
	}
		
	/** The directories that should be watched to determine if the web application needs to be reloaded..*/
	def scanDirectories: Seq[Path] = jettyWebappPath :: Nil
	/** The time in seconds between scans that check whether the web application should be reloaded.*/
	def scanInterval: Int = 3
	/** The port that Jetty runs on. */
	def jettyPort: Int = JettyRun.DefaultPort

	lazy val jettyRestart = jettyStop && jettyRun
	lazy val jettyStop = jettyStopAction
	protected def jettyStopAction = jettyStopTask describedAs(JettyStopDescription)
	
	/** The clean action for a web project is modified so that it first stops jetty if it is running,
	* since the webapp directory will be removed by the clean.*/
	override def cleanAction = super.cleanAction dependsOn jettyStop
	
	/** Redefine the `package` action to make a war file.*/
	override protected def packageAction = packageTask(descendents(temporaryWarPath ##, "*"), warPath, Nil) dependsOn(prepareWebapp) describedAs PackageWarDescription

	/** Redefine the default main artifact to be a war file.*/
	override protected def defaultMainArtifact = Artifact(artifactID, "war", "war")
}

object BasicScalaProject
{
	val CleanDescription =
		"Deletes all generated files (the target directory)."
	val MainCompileDescription =
		"Compiles main sources."
	val TestCompileDescription =
		"Compiles test sources."
	val TestDescription =
		"Runs all tests detected during compilation."
	val TestOnlyDescription =
		"Runs the tests provided as arguments."
	val TestFailedDescription =
		"Runs the tests provided as arguments if they have not succeeded."
	val TestQuickDescription =
		"Runs the tests provided as arguments if they have not succeeded or their dependencies changed."
	val DocDescription =
		"Generates API documentation for main Scala source files using scaladoc."
	val TestDocDescription =
		"Generates API documentation for test Scala source files using scaladoc."
	val RunDescription =
		"Runs the main class for the project with the provided arguments."
	val ConsoleDescription =
		"Starts the Scala interpreter with the project classes on the classpath."
	val ConsoleQuickDescription =
		"Starts the Scala interpreter with the project classes on the classpath without running compile first."
	val PackageDescription =
		"Creates a jar file containing main classes and resources."
	val TestPackageDescription =
		"Creates a jar file containing test classes and resources."
	val DocPackageDescription =
		"Creates a jar file containing generated API documentation."
	val SourcePackageDescription =
		"Creates a jar file containing all main source files and resources."
	val TestSourcePackageDescription =
		"Creates a jar file containing all test source files and resources."
	val ProjectPackageDescription =
		"Creates a zip file containing the entire project, excluding generated files."
	val PackageAllDescription =
		"Executes all package tasks except package-project."
	val DocAllDescription =
		"Generates both main and test documentation."
	val IncrementVersionDescription =
		"Increments the micro part of the version (the third number) by one. (This is only valid for versions of the form #.#.#-*)"
	val ReleaseDescription =
		"Compiles, tests, generates documentation, packages, and increments the version."
		
	private def warnMultipleMainClasses(log: Logger) =
	{
		log.warn("No Main-Class attribute will be added automatically added:")
		log.warn("Multiple classes with a main method were detected.  Specify main class explicitly with:")
		log.warn("     override mainClass = Some(\"className\")")
	}
	private def mapScalaModule(in: Iterable[_], id: String) =
	{
		ScalaVersion.current.toList.flatMap { scalaVersion => 
			in.map(jar => ModuleID(ScalaArtifacts.Organization, id, scalaVersion))
		}
	}
}
object BasicWebScalaProject
{
	val PackageWarDescription =
		"Creates a war file."
	val JettyStopDescription =
		"Stops the Jetty server that was started with the jetty-run action."
	val JettyRunDescription =
		"Starts the Jetty server and serves this project as a web application."
	val JettyDescription =
		"Starts the Jetty server and serves this project as a web application.  Waits until interrupted, so it is suitable to call this batch-style."
}
/** Analyzes the dependencies of a project after compilation.  All methods except `snapshot` return a
* `PathFinder`.  The underlying calculations are repeated for each call to PathFinder.get. */
final class LibraryDependencies(project: Project, conditional: CompileConditional) extends NotNull
{
	/** Library jars located in unmanaged or managed dependency paths.*/
	def libraries: PathFinder = pathFinder(snapshot.libraries)
	/** Library jars located outside of the project.*/
	def external: PathFinder = pathFinder(snapshot.external)
	/** The Scala library jar.*/
	def scalaLibrary: PathFinder = pathFinder(snapshot.scalaLibrary)
	/** The Scala compiler jar.*/
	def scalaCompiler: PathFinder = pathFinder(snapshot.scalaCompiler)
	/** All jar dependencies.*/
	def all: PathFinder = pathFinder(snapshot.all)
	/** The Scala library and compiler jars.*/
	def scalaJars: PathFinder = pathFinder(snapshot.scalaJars)

	/** Returns an object that has all analyzed dependency information frozen at the time of this method call. */
	def snapshot = new Dependencies
	
	private def rootProjectDirectory = project.rootProject.info.projectPath

	final class Dependencies
	{
		import LibraryDependencies._
		val all = conditional.analysis.allExternals.filter(ClasspathUtilities.isArchive).map(_.getAbsoluteFile)
		private[this] val (internal, externalAll) = all.toList.partition(jar => Path.relativize(rootProjectDirectory, jar).isDefined)
		private[this] val (bootScalaJars, librariesNoScala) = internal.partition(isScalaJar)
		private[this] val (externalScalaJars, externalNoScala) = externalAll.partition(isScalaJar)
		val scalaJars = externalScalaJars ::: bootScalaJars
		val (scalaLibrary, scalaCompiler) = scalaJars.partition(isScalaLibraryJar)
		def external = externalNoScala
		def libraries = librariesNoScala
	}

	private def pathFinder(it: => Iterable[File]) = Path.lazyPathFinder(it.map(Path.fromFile))
}
private object LibraryDependencies
{
	private def ScalaLibraryPrefix = ScalaArtifacts.LibraryID
	private def ScalaCompilerPrefix = ScalaArtifacts.CompilerID
	private def ScalaJarPrefixes = List(ScalaCompilerPrefix, ScalaLibraryPrefix)
	private def isScalaJar(file: File) = ClasspathUtilities.isArchive(file) &&  ScalaJarPrefixes.exists(isNamed(file))
	private def isScalaLibraryJar(file: File) = isNamed(file)(ScalaLibraryPrefix)
	private def isNamed(file: File)(name: String) = file.getName.startsWith(name)
	
}