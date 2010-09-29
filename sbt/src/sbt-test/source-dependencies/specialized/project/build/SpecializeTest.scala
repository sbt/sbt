import sbt._

class SpecializeTest(info: ProjectInfo) extends DefaultProject(info)
{
// now on by default in 2.8
//	override def compileOptions = super.compileOptions ++ Seq(CompileOption("-Yspecialize"))
}