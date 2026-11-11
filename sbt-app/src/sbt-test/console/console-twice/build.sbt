scalaVersion := "2.13.12"

lazy val markerFile = settingKey[java.io.File]("marker file written by the console REPL once it starts")

markerFile := baseDirectory.value / "console-ok"

console / initialCommands := {
  val path = markerFile.value.getAbsolutePath.replace("\\", "\\\\")
  s"""java.nio.file.Files.write(java.nio.file.Paths.get("$path"), Array.emptyByteArray)"""
}

TaskKey[Unit]("useDumbTerminal") := { sys.props("jline.terminal") = "none" }
