scalaVersion := "2.13.18"

libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.19.0" % Test

InputKey[Unit]("checkDefinedTestNames") := {
  val actual = (Test / definedTestNames).value
  assert(actual == Seq("example.Test1"), actual)
}
