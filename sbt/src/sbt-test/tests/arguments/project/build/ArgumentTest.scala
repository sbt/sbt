import sbt._

class ArgumentTest(info: ProjectInfo) extends DefaultProject(info)
{
	val st = "org.scalatest" % "scalatest" % "1.3"

	override def testOptions =
		super.testOptions ++
		args("success1", "-n", "test2 test3") ++
		args("success2", "-n", "test2") ++
		args("success3", "-n", "test3") ++
		args("failure1", "-n", "test1") ++
		args("failure2", "-n", "test1 test4") ++
		args("failure3", "-n", "test1 test3")
	def args(path: Path, args: String*): Seq[TestOption] = if(path.exists) TestArgument(args : _*) :: Nil else Nil
}