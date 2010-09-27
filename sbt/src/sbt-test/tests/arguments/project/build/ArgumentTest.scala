import sbt._

class ArgumentTest(info: ProjectInfo) extends DefaultProject(info)
{
	val snap = ScalaToolsSnapshots
	val st = "org.scalatest" % "scalatest" % "1.0.1-for-scala-2.8.0.Beta1-RC7-with-test-interfaces-0.3-SNAPSHOT"

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