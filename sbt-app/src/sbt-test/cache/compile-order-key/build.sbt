Global / localCacheDirectory := baseDirectory.value / "diskcache"

val checkClasses = taskKey[Unit]("asserts the Java class was compiled")

// A distinct project id keeps this fixture's output paths from colliding with same-named
// sibling fixtures in scripted's shared batch directory.
lazy val compileOrderKey = project
  .in(file("."))
  .settings(
    scalaVersion := "3.9.0",
    compileOrder := (
      if ((baseDirectory.value / "mixed.marker").exists) CompileOrder.Mixed
      else CompileOrder.JavaThenScala
    ),
  )

checkClasses := Def.uncached {
  val dir = (compileOrderKey / Compile / classDirectory).value
  val cls = dir / "example" / "J.class"
  assert(cls.exists, s"$cls does not exist")
}
