scalaVersion := "2.13.18"

InputKey[Unit]("checkDiscoveredMainClasses") := {
  val actual = (Compile / discoveredMainClasses).value
  assert(actual == Seq("example.Main"), actual)
}
