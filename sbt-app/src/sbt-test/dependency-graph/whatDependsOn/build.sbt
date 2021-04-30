ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.5"

name := "whatDependsOn"

libraryDependencies ++= Seq(
  "co.fs2" %% "fs2-core" % "1.0.4",
  "org.typelevel" %% "cats-effect" % "3.1.0"
)

val check = TaskKey[Unit]("check")

check := {
  def sanitize(str: String): String = str.split('\n').map(_.trim).mkString("\n")
  def checkOutput(output: String, expected: String): Unit =
    require(sanitize(expected) == sanitize(output), s"Tree should have been [\n${sanitize(expected)}\n] but was [\n${sanitize(output)}\n]")

  val withVersion =
    (Compile / whatDependsOn)
      .toTask(" org.typelevel cats-core_2.13 2.6.0")
      .value
  val expectedGraphWithVersion = {
    """org.typelevel:cats-core_2.13:2.6.0 [S]
      |+-org.typelevel:cats-effect-kernel_2.13:3.1.0 [S]
      |+-org.typelevel:cats-effect-std_2.13:3.1.0 [S]
      || +-org.typelevel:cats-effect_2.13:3.1.0 [S]
      ||   +-whatdependson:whatdependson_2.13:0.1.0-SNAPSHOT [S]
      ||
      |+-org.typelevel:cats-effect_2.13:3.1.0 [S]
      |+-whatdependson:whatdependson_2.13:0.1.0-SNAPSHOT [S]""".stripMargin
  }

  checkOutput(withVersion.trim, expectedGraphWithVersion.trim)

  val withoutVersion =
    (Compile / whatDependsOn)
      .toTask(" org.typelevel cats-core_2.13")
      .value
  val expectedGraphWithoutVersion =
    """org.typelevel:cats-core_2.13:2.6.0 [S]
      |+-org.typelevel:cats-effect-kernel_2.13:3.1.0 [S]
      |+-org.typelevel:cats-effect-std_2.13:3.1.0 [S]
      || +-org.typelevel:cats-effect_2.13:3.1.0 [S]
      ||   +-whatdependson:whatdependson_2.13:0.1.0-SNAPSHOT [S]
      ||
      |+-org.typelevel:cats-effect_2.13:3.1.0 [S]
      |+-whatdependson:whatdependson_2.13:0.1.0-SNAPSHOT [S]""".stripMargin

  checkOutput(withoutVersion.trim, expectedGraphWithoutVersion.trim)

}
