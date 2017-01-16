val scalatest = "org.scalatest" %% "scalatest" % "3.0.1"

lazy val root = (project in file("."))
  .settings(
    scalaVersion in ThisBuild := "2.11.8",
    libraryDependencies += scalatest % Test,
    // testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-f", "result.txt", "-eNDXEHLO")
    testOptions in Configurations.Test ++= {
      def args(path: String, args: String*): Seq[TestOption] =
        if(file(path).exists) Tests.Argument(args : _*) :: Nil
        else Nil
      args("success1", "-n", "test2 test3") ++
      args("success2", "-n", "test2") ++
      args("success3", "-n", "test3") ++
      args("failure1", "-n", "test1") ++
      args("failure2", "-n", "test1 test4") ++
      args("failure3", "-n", "test1 test3")
    }
  )
