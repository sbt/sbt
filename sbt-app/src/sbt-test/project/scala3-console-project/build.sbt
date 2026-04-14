ThisBuild / scalaVersion := "3.7.4"

lazy val markerFile = settingKey[java.io.File]("marker file written by consoleProject REPL when bindings resolve")

lazy val root = project.in(file(".")).settings(
  markerFile := target.value / "console-bindings-ok",
  Global / initialCommands := {
    val path = markerFile.value.getAbsolutePath.replace("\\", "\\\\")
    // Reference `sbt.Keys.compile` so that resolving its `TaskKey` return
    // type goes through the REPL's classloader chain. Before sbt/sbt#7722,
    // this triggered `LinkageError: loader constraint violation` on
    // `sbt.TaskKey` (see PR #9073 review). If the binding or the key
    // reference fails, the marker file is never written.
    s"""val _compileKey = _root_.sbt.Keys.compile
       |_root_.java.nio.file.Files.writeString(_root_.java.nio.file.Paths.get("$path"), currentState.toString.length.toString + "/" + extracted.toString.length.toString + "/" + cpHelpers.toString.length.toString + "/" + _compileKey.key.label)
       |""".stripMargin
  },
)
