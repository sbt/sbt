val check = taskKey[Unit]("")

lazy val root = (project in file("."))
  .settings(
    Compile / scalacOptions += "multi-project",
    check := {
      val xs = (Compile / scalacOptions).value
      assert(xs.toList == List("a", "b", "bare", "c", "multi-project"), s"$xs")
    }
  )

Compile / scalacOptions += "bare"
