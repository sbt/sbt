LibraryDependencies += "org.scalatest" % "scalatest" % "1.3"

TestOptions in Configurations.Test ++= {
	def args(path: String, args: String*): Seq[TestOption] = if(file(path).exists) Test.Argument(args : _*) :: Nil else Nil
//
	args("success1", "-n", "test2 test3") ++
	args("success2", "-n", "test2") ++
	args("success3", "-n", "test3") ++
	args("failure1", "-n", "test1") ++
	args("failure2", "-n", "test1 test4") ++
	args("failure3", "-n", "test1 test3")
}