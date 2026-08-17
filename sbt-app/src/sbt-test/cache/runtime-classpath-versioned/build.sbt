Global / localCacheDirectory := baseDirectory.value / "diskcache"

scalaVersion := "3.8.4"

// packageInternal is used for the Compile/Test-time internal classpath (what app compiles
// against), so a dependency's version bump alone doesn't bust downstream compile caches.
// packageBin (versioned) must still back the Runtime classpath, since that's what `run`
// uses and what tools like sbt-native-packager read to assemble a runnable image.
lazy val checkClasspaths = taskKey[Unit]("Assert Compile classpath uses packageInternal, Runtime classpath uses packageBin")

lazy val foo = project

lazy val app = project
  .dependsOn(foo)
  .settings(
    checkClasspaths := {
      val converter = fileConverter.value
      val compileCp = (Compile / dependencyClasspath).value.map(a => converter.toPath(a.data))
      val runtimeCp = (Runtime / dependencyClasspath).value.map(a => converter.toPath(a.data))
      val binPath = converter.toPath((foo / Compile / packageBin).value)
      val internalPath = converter.toPath((foo / Compile / packageInternal).value)
      val s = streams.value

      s.log.info(s"packageBin      = $binPath")
      s.log.info(s"packageInternal = $internalPath")
      s.log.info(s"Compile classpath = $compileCp")
      s.log.info(s"Runtime classpath = $runtimeCp")

      assert(binPath != internalPath, "packageBin and packageInternal unexpectedly produced the same path")

      assert(
        compileCp.contains(internalPath),
        s"Compile classpath should contain packageInternal's jar ($internalPath), got: $compileCp"
      )
      assert(
        !compileCp.contains(binPath),
        s"Compile classpath should NOT contain packageBin's jar ($binPath), got: $compileCp"
      )

      assert(
        runtimeCp.contains(binPath),
        s"Runtime classpath should contain packageBin's jar ($binPath), got: $runtimeCp"
      )
      assert(
        !runtimeCp.contains(internalPath),
        s"Runtime classpath should NOT contain packageInternal's jar ($internalPath), got: $runtimeCp"
      )
    }
  )

lazy val root = (project in file("."))
  .aggregate(foo, app)
