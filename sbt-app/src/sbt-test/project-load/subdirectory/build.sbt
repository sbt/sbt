lazy val foo = project

@transient
lazy val check = taskKey[Unit]("")

scalaVersion := "3.8.4"
LocalRootProject / check := {
  assert((foo / Compile / scalacOptions).value == List("-Xmacro-settings:a:a"),
    s"${(foo / Compile / scalacOptions).value}")
}
