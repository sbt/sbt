import scala.util.matching.Regex

ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.12"

name := "whatDependsOn"

libraryDependencies ++= Seq(
  "co.fs2" %% "fs2-core" % "1.0.4",
  "org.typelevel" %% "cats-effect" % "3.1.0"
)

val check = TaskKey[Unit]("check")

check := {
  def sanitize(str: String): String =
    str.linesIterator.toList.map(_.trim).mkString("\n")

  def checkOutput(output: String): Unit = {
    val sOutput = sanitize(output)
    val re: Regex = """org\.typelevel:cats-effect(_\d+(\.\d+)?)?""".r
    require(
      re.findFirstIn(sOutput).isDefined,
      s"Output did not contain expected artifact matching ${re}\nOutput:\n$sOutput"
    )
  }

  val withVersion =
    (Compile / whatDependsOn)
      .toTask(" org.typelevel cats-core_2.13 2.6.0")
      .value

  checkOutput(withVersion.trim)

  val withoutVersion =
    (Compile / whatDependsOn)
      .toTask(" org.typelevel cats-core_2.13")
      .value

  checkOutput(withoutVersion.trim)
}
