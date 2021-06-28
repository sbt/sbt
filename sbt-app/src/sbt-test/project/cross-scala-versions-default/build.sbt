scalaVersion := "2.11.6"

TaskKey[Unit]("check") := {
  assert(crossScalaVersions.value == Seq("2.11.6"),
    s"""crossScalaVersions should be Seq("2.11.6") but is ${crossScalaVersions.value}""")
}
