import sbt._

class XSbt(info: ProjectInfo) extends ParentProject(info)
{
	val testDeps = project("test-dependencies", "Dependencies", new TestDependencies(_))
	
	val launchInterfaceSub = project(launchPath / "interface", "Launcher Interface", new InterfaceProject(_), testDeps)
	val launchSub = project(launchPath, "Launcher", new LaunchProject(_), launchInterfaceSub)

	val interfaceSub = project("interface", "Interface", new InterfaceProject(_))

	val controlSub = project(utilPath / "control", "Control", new Base(_))
	val collectionSub = project(utilPath / "collection", "Collections", new Base(_))
	val ioSub = project(utilPath / "io", "IO", new Base(_), controlSub, testDeps)
	val classpathSub = project(utilPath / "classpath", "Classpath", new Base(_))

	val compilerInterfaceSub = project(compilePath / "interface", "Compiler Interface", new CompilerInterfaceProject(_), interfaceSub)

	val ivySub = project("ivy", "Ivy", new IvyProject(_), interfaceSub)
	val logSub = project(utilPath / "log", "Logging", new Base(_))

	val taskSub = project("tasks", "Tasks", new TaskProject(_), controlSub, collectionSub, testDeps)
	val cacheSub = project("cache", "Cache", new CacheProject(_), taskSub, ioSub)
	val compilerSub = project(compilePath, "Compile", new Base(_), interfaceSub, ivySub, ioSub, compilerInterfaceSub)

	def launchPath = path("launch")
	def utilPath = path("util")
	def compilePath = path("compile")

	class LaunchProject(info: ProjectInfo) extends Base(info) with TestWithIO
	{
		val ivy = "org.apache.ivy" % "ivy" % "2.0.0"
	}
	class TestDependencies(info: ProjectInfo) extends DefaultProject(info)
	{
		val sc = "org.scala-tools.testing" % "scalacheck" % "1.5" % "test->default"
		val sp = "org.scala-tools.testing" % "specs" % "1.5.0" % "test->default"
		val ju = "junit" % "junit" % "4.5" % "test->default" // required by specs to compile properly
	}

	override def parallelExecution = true
	class TaskProject(info: ProjectInfo) extends Base(info)
	class CacheProject(info: ProjectInfo) extends Base(info)
	{
		//override def compileOptions = super.compileOptions ++ List(Unchecked,ExplainTypes, CompileOption("-Xlog-implicits"))
	}
	class Base(info: ProjectInfo) extends DefaultProject(info)
	{
		override def scratch = true
	}
	class IvyProject(info: ProjectInfo) extends Base(info) with TestWithIO
	{
		val ivy = "org.apache.ivy" % "ivy" % "2.0.0"
	}
	class InterfaceProject(info: ProjectInfo) extends DefaultProject(info)
	{
		override def mainSources = descendents(mainSourceRoots, "*.java")
		override def compileOrder = CompileOrder.JavaThenScala
	}
	class CompilerInterfaceProject(info: ProjectInfo) extends Base(info) with SourceProject
	{
		// these set up the test so that the classes and resources are both in the output resource directory
		// the main output path is removed so that the plugin (xsbt.Analyzer) is found in the output resource directory so that
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
	override def packagePaths = packageSourcePaths
}