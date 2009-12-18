import sbt._

class SpecializeTest(info: ProjectInfo) extends DefaultProject(info)
{
	override def compileOptions = super.compileOptions ++ Seq(CompileOption("-Yspecialize"))
}