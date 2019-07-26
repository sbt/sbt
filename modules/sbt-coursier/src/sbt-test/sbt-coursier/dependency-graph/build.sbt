scalaVersion := "2.12.8"

libraryDependencies += {
  sys.props("sbt.log.noformat") = "true" // disables colors in coursierWhatDependsOn output
  "org.apache.zookeeper" % "zookeeper" % "3.5.0-alpha"
}

lazy val whatDependsOnCheck = TaskKey[Unit]("whatDependsOnCheck")

import CoursierPlugin.autoImport._

whatDependsOnCheck := {
  val result = (coursierWhatDependsOn in Compile).toTask(" log4j:log4j").value
  val file = new File("whatDependsOnResult.log")
  assert(IO.read(file).toString == result)
}
