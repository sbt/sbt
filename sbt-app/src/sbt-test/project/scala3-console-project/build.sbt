ThisBuild / scalaVersion := "3.8.4"

lazy val markerFile = settingKey[java.io.File]("marker file written by consoleProject REPL when bindings resolve")

lazy val root = project.in(file(".")).settings(
  markerFile := target.value / "console-bindings-ok",
  Global / initialCommands := {
    val path = markerFile.value.getAbsolutePath.replace("\\", "\\\\")
    // Exercise the exact code paths that hit `LinkageError: loader
    // constraint violation` in earlier iterations of sbt/sbt#7722:
    //   1. Resolving `sbt.Keys.compile` loads `sbt.TaskKey` through the
    //      REPL's classloader chain (first regression, pre-#9073).
    //   2. Calling `TaskKey.zipWith(_, Function2)` tripped on
    //      `scala.Function2` being defined twice (second regression,
    //      reported on PR #9073 after the initial fix).
    // If either resolution fails, the marker file is never written.
    s"""val _compileKey = _root_.sbt.Keys.compile
       |val _zipped = _compileKey.zipWith(_compileKey)((_, _) => 0)
       |_root_.java.nio.file.Files.writeString(_root_.java.nio.file.Paths.get("$path"), currentState.toString.length.toString + "/" + extracted.toString.length.toString + "/" + cpHelpers.toString.length.toString + "/" + _compileKey.key.label + "/" + _zipped.getClass.getName)
       |""".stripMargin
  },
)
