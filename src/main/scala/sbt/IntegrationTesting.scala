/* sbt -- Simple Build Tool
 * Copyright 2009  Steven Blundy, Mark Harrah
 */
package sbt

import ScalaProject.{optionsAsString, javaOptionsAsString}

trait IntegrationTesting extends NotNull
{
	/** Override to provide pre-test setup. */
	protected def pretests: Option[String] = None
	/** Override to provide post-test cleanup. */
	protected def posttests: Option[String] = None
}
trait ScalaIntegrationTesting extends IntegrationTesting
{ self: ScalaProject =>

	protected def integrationTestTask(frameworks: Iterable[TestFramework], classpath: PathFinder, analysis: CompileAnalysis, options: => Seq[TestOption]) =
		testTask(frameworks, classpath, analysis, options)
}

trait BasicScalaIntegrationTesting extends BasicIntegrationTesting with MavenStyleIntegrationTestPaths
{ self: BasicScalaProject => }
/** A fully featured integration testing that may be mixed in with any subclass of <code>BasicScalaProject</code>.
 * Pre-suite setup and post-suite cleanup are provide by overriding <code>pretests</code> and <code>posttests</code> respectively.*/
trait BasicIntegrationTesting extends ScalaIntegrationTesting with IntegrationTestPaths with BasicDependencyProject
{
	self: BasicScalaProject =>

	import BasicScalaIntegrationTesting._
	
	lazy val integrationTestCompile = integrationTestCompileAction
	lazy val integrationTest = integrationTestAction

	val integrationTestCompileConditional = new CompileConditional(integrationTestCompileConfiguration)

	protected def integrationTestAction = integrationTestTask(integrationTestFrameworks, integrationTestClasspath, integrationTestCompileConditional.analysis, integrationTestOptions) dependsOn integrationTestCompile describedAs IntegrationTestCompileDescription
	protected def integrationTestCompileAction = integrationTestCompileTask() dependsOn compile describedAs IntegrationTestDescription

	protected def integrationTestCompileTask() = task{ integrationTestCompileConditional.run }

	def integrationTestOptions: Seq[TestOption] = 
		TestSetup(() => pretests) ::
		TestCleanup(() => posttests) ::
		testOptions.toList
	def integrationTestCompileOptions = testCompileOptions
	def javaIntegrationTestCompileOptions: Seq[JavaCompileOption] = testJavaCompileOptions
	
	def integrationTestConfiguration = if(useIntegrationTestConfiguration) Configurations.IntegrationTest else Configurations.Test
	def integrationTestClasspath = fullClasspath(integrationTestConfiguration) +++ optionalClasspath
	
	def integrationTestLabel = "integration-test"
	def integrationTestCompileConfiguration = new IntegrationTestCompileConfig
	
	protected def integrationTestDependencies = new LibraryDependencies(this, integrationTestCompileConditional)

	def integrationTestFrameworks = testFrameworks
	override def useIntegrationTestConfiguration = false
	abstract override def fullUnmanagedClasspath(config: Configuration) =
	{
		val superClasspath = super.fullUnmanagedClasspath(config)
		if(config == integrationTestConfiguration)
			integrationTestCompilePath +++ integrationTestResourcesPath +++ superClasspath
		else
			superClasspath
	}

	class IntegrationTestCompileConfig extends BaseCompileConfig
	{
		def label = integrationTestLabel
		def sourceRoots = integrationTestScalaSourceRoots
		def sources = integrationTestSources
		def outputDirectory = integrationTestCompilePath
		def classpath = integrationTestClasspath
		def analysisPath = integrationTestAnalysisPath
		def baseCompileOptions = integrationTestCompileOptions
		def javaOptions = javaOptionsAsString(javaCompileOptions)
		def testDefinitionClassNames = integrationTestFrameworks.map(_.testSuperClassName)
	}
}

object BasicScalaIntegrationTesting
{
	val IntegrationTestCompileDescription = "Compiles integration test sources."
	val IntegrationTestDescription = "Runs all integration tests detected during compilation."
}
