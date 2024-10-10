val scalatest = "org.scalatest" %% "scalatest" % "3.0.5"

ThisBuild / scalaVersion := "2.12.20"

val foo = settingKey[Seq[String]]("foo")
val checkFoo = inputKey[Unit]("check contents of foo")

lazy val root = (project in file("."))
  .settings(
    foo := Nil,
    checkFoo := {
      val arguments = Def.spaceDelimited("").parsed.mkString(" ")
      assert(foo.value.contains(arguments))
    },
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
