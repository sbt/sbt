// Test for issue #7487: scaladoc should not warn "Flag -project set repeatedly"
scalaVersion := "3.3.1"

lazy val root = (project in file("."))
  .settings(
    name := "scaladoc-project-flag",
    // Add -project flag manually to test that it's not added again
    Compile / scalacOptions += "-project",
    Compile / scalacOptions += "test-project",
    TaskKey[Unit]("check") := {
      val opts = (Compile / doc / scalacOptions).value
      val projectFlags = opts.zipWithIndex.filter(_._1 == "-project")
      // Should have at most one -project flag (the one we added manually)
      // If the fix works, sbt won't add another one
      assert(
        projectFlags.length <= 1,
        s"Expected at most one -project flag, but found ${projectFlags.length} at indices: ${projectFlags.map(_._2).mkString(", ")}. Options: ${opts.mkString(", ")}"
      )
      streams.value.log.info("✓ No duplicate -project flags found")
    }
  )

