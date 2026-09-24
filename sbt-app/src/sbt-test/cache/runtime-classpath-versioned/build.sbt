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

      val selfBin = converter.toPath((Compile / packageBin).value)
      val selfInternal = converter.toPath((Compile / packageInternal).value)
      def checkSelf(name: String, cp: Seq[HashedVirtualFileRef], expectVersioned: Boolean): Unit =
        val paths = cp.map(converter.toPath)
        s.log.info(s"$name = $paths")
        val (expected, unexpected) = if expectVersioned then (selfBin, selfInternal) else (selfInternal, selfBin)
        assert(paths.count(_ == expected) == 1, s"$name should contain $expected exactly once, got: $paths")
        assert(!paths.contains(unexpected), s"$name should NOT contain $unexpected, got: $paths")

      checkSelf("Compile/fullClasspath", (Compile / fullClasspath).value.map(_.data), expectVersioned = false)
      checkSelf("Runtime/fullClasspath", (Runtime / fullClasspath).value.map(_.data), expectVersioned = true)
      checkSelf("Compile/fullClasspathAsJars", (Compile / fullClasspathAsJars).value.map(_.data), expectVersioned = false)
      checkSelf("Runtime/fullClasspathAsJars", (Runtime / fullClasspathAsJars).value.map(_.data), expectVersioned = true)
    }
  )

lazy val checkPluginData = taskKey[Unit]("Assert meta-build jars are not on pluginData.dependencyClasspath")

lazy val root = (project in file("."))
  .aggregate(foo, app)
  .settings(
    checkPluginData := Def.uncached {
      val unit = loadedBuild.value.units(thisProjectRef.value.build).unit
      val deps = unit.plugins.pluginData.dependencyClasspath.map(_.data.id).filter(_.contains("-build_"))
      val defs = unit.plugins.pluginData.definitionClasspath.map(_.data.id).filter(_.contains("-build_"))
      assert(deps.isEmpty, s"dependencyClasspath should NOT contain the meta-build jar, got: $deps")
      assert(defs.nonEmpty, "definitionClasspath should contain the meta-build jar")
    }
  )
