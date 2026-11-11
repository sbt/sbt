TaskKey[Unit]("useDumbTerminal") := { sys.props("jline.terminal") = "none" }
Global / initialCommands := {
  val path = (baseDirectory.value / "console-ok").getAbsolutePath.replace("\\", "\\\\")
  s"""java.nio.file.Files.write(java.nio.file.Paths.get("$path"), Array.emptyByteArray)"""
}
