val checkOptions = taskKey[Unit]("")

lazy val p1 = project
  .settings(
    scalaVersion := "3.0.2",
    checkOptions := {
      assert((Compile / scalacOptions).value == Seq())
      assert((Test / scalacOptions).value    == Seq())
    }
  )

lazy val p2 = project
  .settings(
    scalaVersion := "3.0.2",
    scalaOutputVersion := "3.0.2",
    checkOptions := {
      assert((Compile / scalacOptions).value == Seq())
      assert((Test / scalacOptions).value    == Seq())
    }
  )

lazy val p3 = project
  .settings(
    scalaVersion := "3.1.2-RC2",
    scalaOutputVersion := "3.0.2",
    checkOptions := {
      assert((Compile / scalacOptions).value == Seq("-scala-output-version", "3.0"))
      assert((Test / scalacOptions).value    == Seq("-scala-output-version", "3.0"))
    }
  )
