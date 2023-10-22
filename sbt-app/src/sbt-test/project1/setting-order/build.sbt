val check = taskKey[Unit]("")

lazy val root = (project in file("."))
  .settings(
    Compile / scalacOptions += "multi-project",
    check := {
      val xs = (Compile / scalacOptions).value
      assert(xs.toList == List("multi-project", "a", "b", "bare", "c"), s"$xs")
    }
  )

Compile / scalacOptions += "bare"
