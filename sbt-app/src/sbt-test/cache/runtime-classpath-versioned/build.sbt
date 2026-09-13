Global / localCacheDirectory := baseDirectory.value / "diskcache"

scalaVersion := "3.9.0"

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
      val binPath = converter.toPath((foo / Compile / packageBin).value)
      val internalPath = converter.toPath((foo / Compile / packageInternal).value)
      val s = streams.value

      s.log.info(s"packageBin      = $binPath")
      s.log.info(s"packageInternal = $internalPath")
      assert(binPath != internalPath, "packageBin and packageInternal unexpectedly produced the same path")

      def check(name: String, cp: Seq[HashedVirtualFileRef], expectVersioned: Boolean): Unit =
        val paths = cp.map(converter.toPath)
        s.log.info(s"$name = $paths")
        val (expected, unexpected) = if expectVersioned then (binPath, internalPath) else (internalPath, binPath)
        val expectedDesc = if expectVersioned then "packageBin's" else "packageInternal's"
        val unexpectedDesc = if expectVersioned then "packageInternal's" else "packageBin's"
        assert(paths.contains(expected), s"$name should contain $expectedDesc jar ($expected), got: $paths")
        assert(!paths.contains(unexpected), s"$name should NOT contain $unexpectedDesc jar ($unexpected), got: $paths")

      check("Compile/dependencyClasspath", (Compile / dependencyClasspath).value.map(_.data), expectVersioned = false)
      check("Runtime/dependencyClasspath", (Runtime / dependencyClasspath).value.map(_.data), expectVersioned = true)

      check("Compile/internalDependencyAsJars", (Compile / internalDependencyAsJars).value.map(_.data), expectVersioned = false)
      check("Runtime/internalDependencyAsJars", (Runtime / internalDependencyAsJars).value.map(_.data), expectVersioned = true)

      check("Compile/dependencyClasspathAsJars", (Compile / dependencyClasspathAsJars).value.map(_.data), expectVersioned = false)
      check("Runtime/dependencyClasspathAsJars", (Runtime / dependencyClasspathAsJars).value.map(_.data), expectVersioned = true)

      check("Compile/fullClasspathAsJars", (Compile / fullClasspathAsJars).value.map(_.data), expectVersioned = false)
      check("Runtime/fullClasspathAsJars", (Runtime / fullClasspathAsJars).value.map(_.data), expectVersioned = true)
    }
  )

lazy val root = (project in file("."))
  .aggregate(foo, app)
