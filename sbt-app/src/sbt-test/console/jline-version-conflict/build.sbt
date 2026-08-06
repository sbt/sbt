scalaVersion := "3.9.0-RC4"

lazy val markerFile = settingKey[java.io.File]("marker file written by the console REPL once it starts")

markerFile := target.value / "console-jline-ok"

console / initialCommands := {
  val path = markerFile.value.getAbsolutePath.replace("\\", "\\\\")
  // Scala 3.9 ships JLine 4 while sbt 1.x ships JLine 3. A partial match in
  // MetaBuildLoader's JLine tier used to leave the REPL running jline-reader 4.x
  // against jline-terminal 3.x, which throws NoSuchMethodError before the prompt
  // (#9317). The marker is only written if the REPL actually starts.
  s"""java.nio.file.Files.write(java.nio.file.Paths.get("$path"), Array.emptyByteArray)"""
}
