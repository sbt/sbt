/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package sbt

trait PackagePaths extends NotNull
{
	def jarPath: Path
	def packageTestJar: Path
	def packageDocsJar: Path
	def packageSrcJar: Path
	def packageTestSrcJar: Path
	def packageProjectZip: Path
}
/** These are the paths required by BasicScalaProject.*/
trait ScalaPaths extends PackagePaths
{
	/** A PathFinder that selects all main sources.*/
	def mainSources: PathFinder
	/** A PathFinder that selects all test sources.*/
	def testSources: PathFinder
	def mainSourceRoots: PathFinder
	def testSourceRoots: PathFinder
	/** A PathFinder that selects all main resources.*/
	def mainResources: PathFinder
	/** A PathFinder that selects all test resources. */
	def testResources: PathFinder
	
	def mainCompilePath: Path
	def testCompilePath: Path
	def mainAnalysisPath: Path
	def testAnalysisPath: Path
	def mainDocPath: Path
	def testDocPath: Path
	def graphPath: Path
	def mainResourcesOutputPath: Path
	def testResourcesOutputPath: Path

	/** A PathFinder that selects all the classes compiled from the main sources.*/
	def mainClasses: PathFinder
	/** A PathFinder that selects all the classes compiled from the test sources.*/
	def testClasses: PathFinder
	
	/** Declares all paths to be packaged by the package action.*/
	def packagePaths: PathFinder
	/** Declares all paths to be packaged by the package-test action.*/
	def packageTestPaths: PathFinder
	/** Declares all sources to be packaged by the package-src action.*/
	def packageSourcePaths: PathFinder
	/** Declares all sources to be packaged by the package-test-src action.*/
	def packageTestSourcePaths: PathFinder
	/** Declares all paths to be packaged by the package-project action.*/
	def packageProjectPaths: PathFinder
	
	/** These are the directories that are created when a user makes a new project from sbt.*/
	protected def directoriesToCreate: List[Path]
	/** The directories to which a project writes are listed here and is used
	* to check a project and its dependencies for collisions.*/
	def outputDirectories: Iterable[Path]
	
	def artifactBaseName: String
}

trait BasicScalaPaths extends Project with ScalaPaths
{
	def mainResourcesPath: PathFinder
	def testResourcesPath: PathFinder
	def managedDependencyPath: Path
	def managedDependencyRootPath: Path
	def dependencyPath: Path

	protected def sources(base: PathFinder) = descendents(base, sourceExtensions)	
	protected def sourceExtensions = "*.scala" | "*.java"
	
	def mainSources =
	{
		val normal = sources(mainSourceRoots)
		if(scratch)
			normal +++ (info.projectPath * sourceExtensions)
		else
			normal
	}
	def testSources = sources(testSourceRoots)
	
	def mainResources = descendents(mainResourcesPath ##, "*")
	def testResources = descendents(testResourcesPath ##, "*")
	
	def mainClasses = (mainCompilePath ##) ** "*.class"
	def testClasses = (testCompilePath ##) ** "*.class"
	
	def packagePaths = mainClasses +++ mainResources
	def packageTestPaths = testClasses +++ testResources
	def packageSourcePaths = mainSources +++ mainResources
	def packageTestSourcePaths = testSources +++ testResources
	def packageProjectPaths = descendents( (info.projectPath ##), "*") --- (packageProjectExcludes ** "*")
	protected def packageProjectExcludes: PathFinder =
		outputRootPath +++ managedDependencyRootPath +++
		info.bootPath +++ info.builderProjectOutputPath +++
		info.pluginsOutputPath +++ info.pluginsManagedSourcePath +++ info.pluginsManagedDependencyPath

	override def outputDirectories = outputPath :: managedDependencyPath :: Nil
}

@deprecated trait BasicProjectPaths extends MavenStyleScalaPaths
trait MavenStyleScalaPaths extends BasicScalaPaths with BasicPackagePaths
{
	import BasicProjectPaths._
	
	def outputPath: Path
	
	def sourceDirectoryName = DefaultSourceDirectoryName
	def mainDirectoryName = DefaultMainDirectoryName
	def scalaDirectoryName = DefaultScalaDirectoryName
	def javaDirectoryName = DefaultJavaDirectoryName
	def resourcesDirectoryName = DefaultResourcesDirectoryName
	def testDirectoryName = DefaultTestDirectoryName
	def mainCompileDirectoryName = DefaultMainCompileDirectoryName
	def testCompileDirectoryName = DefaultTestCompileDirectoryName
	def docDirectoryName = DefaultDocDirectoryName
	def apiDirectoryName = DefaultAPIDirectoryName
	def graphDirectoryName = DefaultGraphDirectoryName
	def mainAnalysisDirectoryName = DefaultMainAnalysisDirectoryName
	def testAnalysisDirectoryName = DefaultTestAnalysisDirectoryName
	def mainResourcesOutputDirectoryName = DefautMainResourcesOutputDirectoryName
	def testResourcesOutputDirectoryName = DefautTestResourcesOutputDirectoryName
	
	def sourcePath = path(sourceDirectoryName)
	
	def mainSourcePath = sourcePath / mainDirectoryName
	def mainScalaSourcePath = mainSourcePath / scalaDirectoryName
	def mainJavaSourcePath = mainSourcePath / javaDirectoryName
	def mainResourcesPath = mainSourcePath / resourcesDirectoryName
	def mainDocPath = docPath / mainDirectoryName / apiDirectoryName
	def mainCompilePath = outputPath / mainCompileDirectoryName
	def mainResourcesOutputPath = outputPath / mainResourcesOutputDirectoryName
	def mainAnalysisPath = outputPath / mainAnalysisDirectoryName
	
	def testSourcePath = sourcePath / testDirectoryName
	def testJavaSourcePath = testSourcePath / javaDirectoryName
	def testScalaSourcePath = testSourcePath / scalaDirectoryName
	def testResourcesPath = testSourcePath / resourcesDirectoryName
	def testDocPath = docPath / testDirectoryName / apiDirectoryName
	def testCompilePath = outputPath / testCompileDirectoryName
	def testResourcesOutputPath = outputPath / testResourcesOutputDirectoryName
	def testAnalysisPath = outputPath / testAnalysisDirectoryName
	
	def docPath = outputPath / docDirectoryName
	def graphPath = outputPath / graphDirectoryName
		
	/** These are the directories that are created when a user makes a new project from sbt.*/
	protected def directoriesToCreate: List[Path] =
		dependencyPath ::
		mainScalaSourcePath ::
		mainResourcesPath ::
		testScalaSourcePath ::
		testResourcesPath ::
		Nil
		
	def mainSourceRoots = (mainJavaSourcePath##) +++ (mainScalaSourcePath##)
	def testSourceRoots = (testJavaSourcePath##) +++ (testScalaSourcePath##)
}

trait BasicPackagePaths extends ScalaPaths with PackagePaths
{
	def outputPath: Path
	
	def defaultJarBaseName: String = artifactBaseName
	def defaultJarName = defaultJarBaseName + ".jar"
	def jarPath = outputPath / defaultJarName
	def packageTestJar = defaultJarPath("-test.jar")
	def packageDocsJar = defaultJarPath("-docs.jar")
	def packageSrcJar= defaultJarPath("-src.jar")
	def packageTestSrcJar = defaultJarPath("-test-src.jar")
	def packageProjectZip = defaultJarPath("-project.zip")
	def defaultJarPath(extension: String) = outputPath / (artifactBaseName + extension)
}

object BasicProjectPaths
{
	val DefaultSourceDirectoryName = "src"
	val DefaultMainCompileDirectoryName = "classes"
	val DefaultTestCompileDirectoryName = "test-classes"
	val DefaultDocDirectoryName = "doc"
	val DefaultAPIDirectoryName = "api"
	val DefaultGraphDirectoryName = "graph"
	val DefaultMainAnalysisDirectoryName = "analysis"
	val DefaultTestAnalysisDirectoryName = "test-analysis"
	val DefautMainResourcesOutputDirectoryName = "resources"
	val DefautTestResourcesOutputDirectoryName = "test-resources"
	
	val DefaultMainDirectoryName = "main"
	val DefaultScalaDirectoryName = "scala"
	val DefaultJavaDirectoryName = "java"
	val DefaultResourcesDirectoryName = "resources"
	val DefaultTestDirectoryName = "test"
	
	// forwarders to new locations
	def BootDirectoryName = Project.BootDirectoryName
	def DefaultManagedDirectoryName = BasicDependencyPaths.DefaultManagedDirectoryName
	def DefaultDependencyDirectoryName = BasicDependencyPaths.DefaultDependencyDirectoryName
}

trait WebScalaPaths extends ScalaPaths
{
	def temporaryWarPath: Path
	def webappResources: PathFinder
	def jettyContextPath: String
	def warPath: Path
}
@deprecated trait WebProjectPaths extends MavenStyleWebScalaPaths
trait MavenStyleWebScalaPaths extends WebScalaPaths with MavenStyleScalaPaths
{
	import WebProjectPaths._
	def temporaryWarPath = outputPath / webappDirectoryName
	def webappPath = mainSourcePath / webappDirectoryName
	def webappDirectoryName = DefaultWebappDirectoryName
	def jettyContextPath = DefaultJettyContextPath
	def defaultWarName = defaultJarBaseName + ".war"
	def warPath = outputPath / defaultWarName
	/** Additional files to include in the web application. */
	protected def extraWebappFiles: PathFinder = Path.emptyPathFinder
	def webappResources = descendents(webappPath ##, "*") +++ extraWebappFiles
}
object WebProjectPaths
{
	val DefaultWebappDirectoryName = "webapp"
	val DefaultJettyContextPath = "/"
}

/** Defines default paths for a webstart project.  It directly extends WebstartOptions to make
* it easy to implement and override webstart options in the common case of one webstartTask per
* project.*/
trait WebstartPaths extends ScalaPaths
{
	import WebstartPaths._
	
	def outputPath: Path
	def jnlpPath: Path
	
	def webstartOutputDirectory = outputPath / webstartDirectoryName
	
	def jnlpFile = webstartOutputDirectory / jnlpFileName
	def webstartLibDirectory = webstartOutputDirectory / webstartLibName
	def webstartZip: Option[Path] = Some(outputPath / webstartZipName)
	def jnlpResourcesPath = jnlpPath / BasicProjectPaths.DefaultResourcesDirectoryName
	
	def webstartLibName = DefaultWebstartLibName
	def webstartDirectoryName = DefaultWebstartDirectoryName
	
	def webstartZipName: String
	def jnlpFileName: String
}
object WebstartPaths
{
	val DefaultWebstartDirectoryName = "webstart"
	val DefaultJnlpName = "jnlp"
	val DefaultWebstartLibName = "lib"
}
trait MavenStyleWebstartPaths extends WebstartPaths with MavenStyleScalaPaths
{
	import WebstartPaths._
	def jnlpPath = mainSourcePath / DefaultJnlpName
	def webstartMainJar = jarPath
	def jnlpFileName = DefaultJnlpFileName
	def webstartZipName = artifactBaseName + ".zip"
	def DefaultJnlpFileName = artifactBaseName + ".jnlp"
}

trait IntegrationTestPaths extends NotNull
{
	def integrationTestSources: PathFinder
	def integrationTestScalaSourceRoots: PathFinder
	def integrationTestResourcesPath: Path

	def integrationTestCompilePath: Path
	def integrationTestAnalysisPath: Path
}
trait BasicIntegrationTestPaths extends IntegrationTestPaths
{
	def integrationTestScalaSourcePath: Path
	def integrationTestScalaSourceRoots: PathFinder = integrationTestScalaSourcePath
	def integrationTestSources = sources(integrationTestScalaSourceRoots)
	protected def sources(base: PathFinder): PathFinder
}
trait MavenStyleIntegrationTestPaths extends BasicIntegrationTestPaths with MavenStyleScalaPaths
{
	import IntegrationTestPaths._

	def integrationTestDirectoryName = DefaultIntegrationTestDirectoryName
	def integrationTestCompileDirectoryName = DefaultIntegrationTestCompileDirectoryName
	def integrationTestAnalysisDirectoryName = DefaultIntegrationTestAnalysisDirectoryName

	def integrationTestSourcePath = sourcePath / integrationTestDirectoryName
	def integrationTestScalaSourcePath = integrationTestSourcePath / scalaDirectoryName
	def integrationTestResourcesPath = integrationTestSourcePath / resourcesDirectoryName

	def integrationTestCompilePath = outputPath / integrationTestCompileDirectoryName
	def integrationTestAnalysisPath = outputPath / integrationTestAnalysisDirectoryName
}

object IntegrationTestPaths
{
	val DefaultIntegrationTestDirectoryName = "it"
	val DefaultIntegrationTestCompileDirectoryName = "it-classes"
	val DefaultIntegrationTestAnalysisDirectoryName = "it-analysis"
}