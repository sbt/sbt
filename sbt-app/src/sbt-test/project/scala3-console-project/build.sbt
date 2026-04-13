ThisBuild / scalaVersion := "3.7.4"

lazy val markerFile = settingKey[java.io.File]("marker file written by consoleProject REPL when bindings resolve")

lazy val root = project.in(file(".")).settings(
  markerFile := target.value / "console-bindings-ok",
  Global / initialCommands := {
    val path = markerFile.value.getAbsolutePath.replace("\\", "\\\\")
    s"""_root_.java.nio.file.Files.writeString(_root_.java.nio.file.Paths.get("$path"), currentState.toString.length.toString + "/" + extracted.toString.length.toString + "/" + cpHelpers.toString.length.toString)
       |""".stripMargin
  },
)
