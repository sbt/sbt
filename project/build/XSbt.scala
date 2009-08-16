import sbt._

class XSbt(info: ProjectInfo) extends ParentProject(info)
{
	def utilPath = path("util")

	val controlSub = project(utilPath / "control", "Control", new Base(_))
	val collectionSub = project(utilPath / "collection", "Collections", new Base(_))
	val ioSub = project(utilPath / "io", "IO", new Base(_),controlSub)
	val classpathSub = project(utilPath / "classpath", "Classpath", new Base(_))

	val ivySub = project("ivy", "Ivy", new IvyProject(_))
	val logSub = project(utilPath / "log", "Logging", new Base(_))

	val taskSub = project("tasks", "Tasks", new TaskProject(_), controlSub, collectionSub)
	val cacheSub = project("cache", "Cache", new CacheProject(_), taskSub, ioSub)

	override def parallelExecution = true
	class TaskProject(info: ProjectInfo) extends Base(info)
	{
		val sc = "org.scala-tools.testing" % "scalacheck" % "1.5" % "test->default"
	}
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
}