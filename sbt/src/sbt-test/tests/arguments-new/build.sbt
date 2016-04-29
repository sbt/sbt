scalaVersion := "2.10.6"
libraryDependencies += "org.scalatest" % "scalatest_2.10" % "2.0.M6-SNAP28" % "test"


testOptions in Configurations.Test ++= {
	def args(path: String, args: String*): Seq[TestOption] = if(file(path).exists) Tests.Argument(args : _*) :: Nil else Nil
//
	args("success1", "-n", "test2 test3") ++
	args("success2", "-n", "test2") ++
	args("success3", "-n", "test3") ++
	args("failure1", "-n", "test1") ++
	args("failure2", "-n", "test1 test4") ++
	args("failure3", "-n", "test1 test3")
}