import sbt._

class XSbt(info: ProjectInfo) extends ParentProject(info)
{
		/* Subproject declarations*/

	val launchInterfaceSub = project(launchPath / "interface", "Launcher Interface", new InterfaceProject(_))
	val launchSub = project(launchPath, "Launcher", new LaunchProject(_), launchInterfaceSub)

	val interfaceSub = project("interface", "Interface", new InterfaceProject(_))

	val controlSub = project(utilPath / "control", "Control", new Base(_))
	val collectionSub = project(utilPath / "collection", "Collections", new Base(_))
	val ioSub = project(utilPath / "io", "IO", new IOProject(_), controlSub)
	val classpathSub = project(utilPath / "classpath", "Classpath", new Base(_))

	val compileInterfaceSub = project(compilePath / "interface", "Compiler Interface Src", new CompilerInterfaceProject(_), interfaceSub)

	val ivySub = project("ivy", "Ivy", new IvyProject(_), interfaceSub)
	val logSub = project(utilPath / "log", "Logging", new Base(_))

	val taskSub = project(tasksPath, "Tasks", new TaskProject(_), controlSub, collectionSub)
	val cacheSub = project(cachePath, "Cache", new CacheProject(_), taskSub, ioSub)
	val trackingSub = project(cachePath / "tracking", "Tracking", new Base(_), cacheSub)
	val compilerSub = project(compilePath, "Compile", new CompileProject(_),
		launchInterfaceSub, interfaceSub, ivySub, ioSub, classpathSub, compileInterfaceSub)
	val stdTaskSub = project(tasksPath / "standard", "Standard Tasks", new Base(_), trackingSub, compilerSub)

		/* Multi-subproject paths */

	def cachePath = path("cache")
	def tasksPath = path("tasks")
	def launchPath = path("launch")
	def utilPath = path("util")
	def compilePath = path("compile")

	//run in parallel
	override def parallelExecution = true

		/* Subproject configurations*/
	class LaunchProject(info: ProjectInfo) extends Base(info) with TestWithIO with TestDependencies
	{
		val ivy = "org.apache.ivy" % "ivy" % "2.0.0"
		// to test the retrieving and loading of the main sbt, we package and publish the test classes to the local repository
		override def defaultMainArtifact = Artifact(idWithVersion)
		override def projectID = ModuleID(organization, idWithVersion, "test-" + version)
		override def packageAction = packageTask(packageTestPaths, outputPath / (idWithVersion + "-" + projectID.revision +".jar"), packageOptions).dependsOn(rawTestCompile)
		override def deliverProjectDependencies = Nil
		def idWithVersion = "xsbt_" + ScalaVersion.currentString
		lazy val rawTestCompile = super.testCompileAction
		override def testCompileAction = publishLocal dependsOn(rawTestCompile)
	}
	trait TestDependencies extends Project
	{
		val sc = "org.scala-tools.testing" % "scalacheck" % "1.5" % "test->default"
		val sp = "org.scala-tools.testing" % "specs" % "1.5.0" % "test->default"
		val ju = "junit" % "junit" % "4.5" % "test->default" // required by specs to compile properly
	}

	class IOProject(info: ProjectInfo) extends Base(info) with TestDependencies
	class TaskProject(info: ProjectInfo) extends Base(info) with TestDependencies
	class CacheProject(info: ProjectInfo) extends Base(info)
	{
		// these compilation options are useful for debugging caches and task composition
		//override def compileOptions = super.compileOptions ++ List(Unchecked,ExplainTypes, CompileOption("-Xlog-implicits"))
	}
	class Base(info: ProjectInfo) extends DefaultProject(info) with ManagedBase
	{
		override def scratch = true
	}
	class CompileProject(info: ProjectInfo) extends Base(info)
	{
		override def testCompileAction = super.testCompileAction dependsOn(launchSub.testCompile, compileInterfaceSub.`package`, interfaceSub.`package`)
		 // don't include launch interface in published dependencies because it will be provided by launcher
		override def deliverProjectDependencies = Set(super.deliverProjectDependencies.toSeq : _*) - launchInterfaceSub.projectID
		override def testClasspath = super.testClasspath +++ launchSub.testClasspath +++ compileInterfaceSub.jarPath +++ interfaceSub.jarPath
		override def compileOptions = super.compileOptions ++ Seq(CompileOption("-Xno-varargs-conversion")) //needed for invoking nsc.scala.tools.Main.process(Array[String])
	}
	class IvyProject(info: ProjectInfo) extends Base(info) with TestWithIO
	{
		val ivy = "org.apache.ivy" % "ivy" % "2.0.0"
	}
	class InterfaceProject(info: ProjectInfo) extends DefaultProject(info) with ManagedBase
	{
		// ensure that interfaces are only Java sources and that they cannot reference Scala classes
		override def mainSources = descendents(mainSourceRoots, "*.java")
		override def compileOrder = CompileOrder.JavaThenScala
	}
	class CompilerInterfaceProject(info: ProjectInfo) extends Base(info) with SourceProject
	{
		// these set up the test environment so that the classes and resources are both in the output resource directory
		// the main compile path is removed so that the plugin (xsbt.Analyzer) is found in the output resource directory so that
		// the tests can configure that directory as -Xpluginsdir (which requires the scalac-plugin.xml and the classes to be together)
		override def testCompileAction = super.testCompileAction dependsOn(packageForTest, ioSub.testCompile)
		override def mainResources = super.mainResources +++ "scalac-plugin.xml"
		override def testClasspath = (super.testClasspath --- super.mainCompilePath) +++ ioSub.testClasspath  +++ testPackagePath
		def testPackagePath = outputPath / "test.jar"
		lazy val packageForTest = packageTask(mainClasses +++ mainResources, testPackagePath, packageOptions).dependsOn(compile)
	}
	trait TestWithIO extends BasicScalaProject
	{
		// use IO from tests
		override def testCompileAction = super.testCompileAction dependsOn(ioSub.testCompile)
		override def testClasspath = super.testClasspath +++ ioSub.testClasspath
	}
}

trait SourceProject extends BasicScalaProject
{
	override final def crossScalaVersions = Set.empty // don't need to cross-build a source package
	override def packagePaths = mainResources +++ mainSources // the default artifact is a jar of the main sources and resources
}
trait ManagedBase extends BasicScalaProject
{
	override def deliverScalaDependencies = Nil //
	override def crossScalaVersions = Set("2.7.5")
	override def managedStyle = ManagedStyle.Ivy
	override def useDefaultConfigurations = false
	val defaultConf = Configurations.Default
	val testConf = Configurations.Test
}