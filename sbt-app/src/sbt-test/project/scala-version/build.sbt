@transient
lazy val checkScalaVersionWarning = taskKey[Unit]("")

// exempt publish skipped projects
lazy val `scala-version-root` = (project in file("."))
  .settings(
    name := "scala-version-root",
    checkScalaVersionWarning := {
      val state = Keys.state.value
      val logging = state.globalLogging
      val sv = scalaVersion.value
      val contents = IO.read(logging.backing.file)
      assert(contents.contains(s"""scalaVersion for subproject nievab1 fell back to a default value $sv"""))
      assert(!contents.contains(s"""scalaVersion for subproject scala-version-root fell back to a default value $sv"""))
      assert(!contents.contains(s"""scalaVersion for subproject nievab2 fell back to a default value $sv"""))
      assert(!contents.contains(s"""scalaVersion for subproject nievab3 fell back to a default value $sv"""))
      assert(!contents.contains(s"""scalaVersion for subproject nievab4 fell back to a default value $sv"""))
      ()
    },
    publish / skip := true,
  )

lazy val nievab1 = project

// exempt plugin projects
lazy val nievab2 = project
  .enablePlugins(SbtPlugin)

// exempt Java projects
lazy val nievab3 = project
  .settings(
    autoScalaLibrary := false,
  )

// exempt SCALA_HOME projects
lazy val nievab4 = project
  .settings(
    managedScalaInstance := false,
  )
