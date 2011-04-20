/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah, David MacIver, Josh Cough
 */
package sbt

/** The default project when no project is explicitly configured and the common base class for
* configuring a project.*/
class DefaultProject(val info: ProjectInfo) extends BasicScalaProject with MavenStyleScalaPaths
class DefaultWebProject(val info: ProjectInfo) extends BasicWebScalaProject with MavenStyleWebScalaPaths


import BasicScalaProject._
import ScalaProject.{optionsAsString, javaOptionsAsString}
import java.io.File
import java.net.URLClassLoader
import java.util.jar.Attributes
import scala.collection.mutable.ListBuffer

/** This class defines concrete instances of actions from ScalaProject using overridable paths,
* options, and configuration. */
abstract class BasicScalaProject extends ScalaProject with BasicDependencyProject with ScalaPaths
{
	/** The options provided to the 'doc' and 'docTest' actions.*/
	def documentOptions: Seq[ScaladocOption] =
		documentTitle(name + " " + version + " API") ::
		(if(isScala27) only27Options else Nil)
	private def only27Options =
		windowTitle(name + " " + version + " API") :: Nil
	/** The options provided to the 'test' action..*/
	def testOptions: Seq[TestOption] =
		TestListeners(testListeners) ::
		TestFilter(includeTest) ::
		Nil

	private def succeededTestPath = testAnalysisPath / "succeeded-tests"
	protected final def quickOptions(failedOnly: Boolean) =
	{
		val path = succeededTestPath
		val analysis = testCompileConditional.analysis
		TestFilter(new impl.TestQuickFilter(analysis, failedOnly, path, log))  :: TestListeners(new impl.TestStatusReporter(path, log) :: Nil) :: Nil
	}


	protected def testAction = defaultTestTask(testOptions)
	protected def testOnlyAction = testOnlyTask(testOptions)
	protected def testOnlyTask(testOptions: => Seq[TestOption]) = testQuickMethod(testCompileConditional.analysis, testOptions)(o => defaultTestTask(o)) describedAs (TestOnlyDescription)
	protected def testQuickAction = defaultTestQuickMethod(false, testOptions) describedAs (TestQuickDescription)
	protected def testFailedAction = defaultTestQuickMethod(true, testOptions) describedAs (TestFailedDescription)
	protected def defaultTestQuickMethod(failedOnly: Boolean, testOptions: => Seq[TestOption]) =
		testQuickMethod(testCompileConditional.analysis, testOptions)(options => defaultTestTask(quickOptions(failedOnly) ::: options.toList))
	protected def defaultTestTask(testOptions: => Seq[TestOption]) =
		testTask(testFrameworks, testClasspath, testCompileConditional.analysis, testOptions).dependsOn(testCompile, copyResources, copyTestResources) describedAs TestDescription

	protected def docAllAction = (doc && docTest) describedAs DocAllDescription
	protected def packageAllAction = task { None} dependsOn(`package`, packageTest, packageSrc, packageTestSrc, packageDocs) describedAs PackageAllDescription
	protected def graphSourcesAction = graphSourcesTask(graphSourcesPath, mainSourceRoots, mainCompileConditional.analysis).dependsOn(compile)
	protected def graphPackagesAction = graphPackagesTask(graphPackagesPath, mainSourceRoots, mainCompileConditional.analysis).dependsOn(compile)
	protected def incrementVersionAction = task { incrementVersionNumber(); None } describedAs IncrementVersionDescription

	lazy val javap = javapTask(runClasspath, mainCompileConditional, mainCompilePath)
	lazy val testJavap = javapTask(testClasspath, testCompileConditional, testCompilePath)

}
abstract class BasicWebScalaProject extends BasicScalaProject with WebScalaProject with WebScalaPaths
{ p =>
	import BasicWebScalaProject._
	override def watchPaths = super.watchPaths +++ webappResources

	/** Override this to define paths that `prepare-webapp` and `package` should ignore.
	* They will not be pruned by prepare-webapp and will not be included in the war.*/
	def webappUnmanaged: PathFinder = Path.emptyPathFinder

	lazy val prepareWebapp = prepareWebappAction
	protected def prepareWebappAction =
		prepareWebappTask(webappResources, temporaryWarPath, webappClasspath, mainDependencies.scalaJars, webappUnmanaged) dependsOn(compile, copyResources)

	lazy val jettyInstance = new JettyRunner(jettyConfiguration)

	def jettyConfiguration: JettyConfiguration =
		new DefaultJettyConfiguration
		{
			def classpath = jettyRunClasspath
			def jettyClasspath = p.jettyClasspath
			def war = jettyWebappPath
			def contextPath = jettyContextPath
			def classpathName = "test"
			def parentLoader = buildScalaInstance.loader
			def scanDirectories = Path.getFiles(p.scanDirectories).toSeq
			def scanInterval = p.scanInterval
			def port = jettyPort
			def log = p.log
			def jettyEnv = jettyEnvXml
			def webDefaultXml = jettyWebDefaultXml
		}
	/** This is the classpath used to determine what classes, resources, and jars to put in the war file.*/
	def webappClasspath = publicClasspath
	/** This is the classpath containing Jetty.*/
	def jettyClasspath = testClasspath --- jettyRunClasspath
	/** This is the classpath containing the web application.*/
	def jettyRunClasspath = publicClasspath
	def jettyWebappPath = temporaryWarPath
	lazy val jettyRun = jettyRunAction
	lazy val jetty = task { idle() } dependsOn(jettyRun) describedAs(JettyDescription)
	protected def jettyRunAction = jettyRunTask(jettyInstance) dependsOn(prepareWebapp) describedAs(JettyRunDescription)
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
	def scanInterval: Int = JettyRunner.DefaultScanInterval
	/** The port that Jetty runs on. */
	def jettyPort: Int = JettyRunner.DefaultPort

	def jettyEnvXml : Option[File] = None
	def jettyWebDefaultXml : Option[File] = None

	lazy val jettyReload = task { jettyInstance.reload(); None } describedAs(JettyReloadDescription)
	lazy val jettyRestart = jettyStop && jettyRun
	lazy val jettyStop = jettyStopAction
	protected def jettyStopAction = jettyStopTask(jettyInstance) describedAs(JettyStopDescription)

	/** The clean action for a web project is modified so that it first stops jetty if it is running,
	* since the webapp directory will be removed by the clean.*/
	override def cleanAction = super.cleanAction dependsOn jettyStop

	/** Redefine the `package` action to make a war file.*/
	override protected def packageAction = packageWarAction(temporaryWarPath, webappUnmanaged, warPath, Nil) dependsOn(prepareWebapp) describedAs PackageWarDescription

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
	val TestRunDescription =
		"Runs a test class with a main method with the provided arguments."
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
	val CopyResourcesDescription =
		"Copies resources to the target directory where they can be included on classpaths."
	val CopyTestResourcesDescription =
		"Copies test resources to the target directory where they can be included on the test classpath."

	private def warnMultipleMainClasses(log: Logger) =
	{
		log.warn("No Main-Class attribute will be added automatically added:")
		log.warn("Multiple classes with a main method were detected.  Specify main class explicitly with:")
		log.warn("     override def mainClass = Some(\"className\")")
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
	val JettyReloadDescription =
		"Forces a reload of a web application running in a Jetty server started by 'jetty-run'.  Does nothing if Jetty is not running."
}
