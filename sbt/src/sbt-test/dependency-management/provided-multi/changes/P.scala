import sbt._

class P(info: ProjectInfo) extends ParentProject(info)
{
	val a = project("a", "A", new A(_))
	val b = project("b", "B", new B(_), a)

	def aLibrary = "org.scala-tools.sbinary" %% "sbinary" % "0.3" % "provided"

	class A(info: ProjectInfo) extends DefaultProject(info)
	{
		val a = aLibrary
	}
	class B(info: ProjectInfo) extends DefaultWebProject(info)
	{
		override def libraryDependencies =
			if("declare.lib".asFile.exists) Set(aLibrary) else Set()
	}
}