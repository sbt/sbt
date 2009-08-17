import sbt._

class XSbt(info: ProjectInfo) extends ParentProject(info)
{
	def utilPath = path("util")
	def compilePath = path("compile")
	
	val commonDeps = project("common", "Dependencies", new CommonDependencies(_))
	val interfaceSub = project("interface", "Interface", new InterfaceProject(_))

	val controlSub = project(utilPath / "control", "Control", new Base(_))
	val collectionSub = project(utilPath / "collection", "Collections", new Base(_))
	val ioSub = project(utilPath / "io", "IO", new Base(_), controlSub, commonDeps)
	val classpathSub = project(utilPath / "classpath", "Classpath", new Base(_))

	val analysisPluginSub = project(compilePath / "plugin", "Analyzer Compiler Plugin", new Base(_), interfaceSub)
	val compilerInterfaceSub = project(compilePath / "interface", "Compiler Interface", new Base(_), interfaceSub)

	val ivySub = project("ivy", "Ivy", new IvyProject(_))
	val logSub = project(utilPath / "log", "Logging", new Base(_))

	val taskSub = project("tasks", "Tasks", new TaskProject(_), controlSub, collectionSub, commonDeps)
	val cacheSub = project("cache", "Cache", new CacheProject(_), taskSub, ioSub)
	val compilerSub = project(compilePath, "Compile", new Base(_), interfaceSub, ivySub, analysisPluginSub, ioSub, compilerInterfaceSub)

	class CommonDependencies(info: ProjectInfo) extends ParentProject(info)
	{
		val sc = "org.scala-tools.testing" % "scalacheck" % "1.5" % "test->default"
	}

	override def parallelExecution = true
	class TaskProject(info: ProjectInfo) extends Base(info)
	class CacheProject(info: ProjectInfo) extends Base(info)
	{
		//override def compileOptions = super.compileOptions ++ List(Unchecked,ExplainTypes, CompileOption("-Xlog-implicits"))
	}
	class Base(info: ProjectInfo) extends DefaultProject(info) with AssemblyProject
	{
		override def scratch = true
	}
	class IvyProject(info: ProjectInfo) extends Base(info)
	{
		val ivy = "org.apache.ivy" % "ivy" % "2.0.0"
	}
	class InterfaceProject(info: ProjectInfo) extends DefaultProject(info)
	{
		override def sourceExtensions: NameFilter = "*.java"
		override def compileOrder = CompileOrder.JavaThenScala
	}
}