scalaVersion := "2.13.12"

lazy val markerFile = settingKey[java.io.File]("marker file written by the console REPL when JDK platform classes load")

markerFile := target.value / "console-jdk-platform-ok"

console / initialCommands := {
  val path = markerFile.value.getAbsolutePath.replace("\\", "\\\\")
  // java.sql lives in a JDK platform module. Loading it through the console REPL's
  // classloader chain threw SecurityException("Prohibited package name: java.sql")
  // before the platform-loader fallback (#4328): `ts` covers the top-down request,
  // `mts` covers supertype resolution for a class defined from the project classpath.
  // The REPL traps exceptions, so the assertion is a marker file: it is only written
  // if both classes actually load.
  s"""val ts = new java.sql.Timestamp(0L)
     |val mts = new MyTimestamp
     |java.nio.file.Files.write(java.nio.file.Paths.get("$path"), (ts.getClass.getName + " " + mts.getClass.getName).getBytes("UTF-8"))
     |""".stripMargin
}
