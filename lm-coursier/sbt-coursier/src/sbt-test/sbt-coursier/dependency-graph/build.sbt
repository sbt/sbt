scalaVersion := "2.12.8"

libraryDependencies += {
  sys.props("sbt.log.noformat") = "true" // disables colors in coursierWhatDependsOn output
  "org.apache.zookeeper" % "zookeeper" % "3.5.0-alpha"
}

lazy val whatDependsOnCheck = TaskKey[Unit]("whatDependsOnCheck")

import java.nio.file.{Files, Paths}
import CoursierPlugin.autoImport._

whatDependsOnCheck := {
  val result = (coursierWhatDependsOn in Compile).toTask(" log4j:log4j").value
    .replace(System.lineSeparator(), "\n")
  val expected = new String(Files.readAllBytes(Paths.get("whatDependsOnResult.log")), "UTF-8")
  assert(expected == result, s"Expected '$expected', got '$result'")
}
