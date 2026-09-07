lazy val check = taskKey[Unit]("")

lazy val core = (projectMatrix in file("core"))
  .settings(
    // Def.uncached: Seq[VirtualAxis] has no HashWriter, so the task cannot be cached
    check := Def.uncached {
      val base = projectMatrixBaseDirectory.value
      def dir(parts: String*): File = parts.foldLeft(base)(_ / _)
      val srcs = (Compile / unmanagedSourceDirectories).value.toSet
      // `scala-<axis>`, then `scala-<epoch>` where it differs, then `scala`
      val axis = virtualAxes.value.collectFirst { case a: VirtualAxis.ScalaVersionAxis => a.value }
      val variants = axis.get match {
        case "2.13" => Set("scala-2.13", "scala-2", "scala")
        case "3"    => Set("scala-3", "scala")
        case "3.3"  => Set("scala-3.3", "scala-3", "scala")
        // a full version, as `CrossVersion.full` and semanticdb use
        case "2.13.17" => Set("scala-2.13.17", "scala-2.13", "scala-2", "scala")
      }
      // shared, the row's own platform, and each group short of every platform
      val trees = Seq("shared", "jvm", "js-jvm", "jvm-native")
      val wanted = trees.flatMap(t => variants.map(v => Seq(t, "src", "main", v))) ++ Seq(
        // the shared trees carry Scala alone; only the row's own platform tree carries java
        Seq("jvm", "src", "main", "java"),
      )
      wanted.foreach(p => assert(!srcs(dir(p*)), s"unexpected ${dir(p*)} in $srcs"))
      // the default layout
      val defaults = Seq(Seq("src", "main", "scala"), Seq("src", "main", "scalajvm"))
      defaults.foreach(p => assert(srcs(dir(p*)), s"missing ${dir(p*)} in $srcs"))
      val sharedMain = dir("shared", "src", "main")
      val shared = srcs.filter(_.getParentFile == sharedMain).map(_.getName)
      assert(shared.isEmpty, s"$shared under $sharedMain")
      // the group of every platform is what shared is
      assert(!srcs(dir("js-jvm-native", "src", "main", "scala")), "no all-platform group")
      val res = (Compile / unmanagedResourceDirectories).value.toSet
      assert(!res(dir("shared", "src", "main", "resources")), s"unexpected resources in $res")
      val tests = (Test / unmanagedSourceDirectories).value.toSet
      assert(!tests(dir("shared", "src", "test", "scala")), s"unexpected test tree in $tests")
    },
  )
  .jvmPlatform(scalaVersions = Seq("2.13.18", "3.9.0"))
  // a row whose axis names the minor version, which `jvmPlatform` never builds
  .customRow(true, Seq(VirtualAxis.jvm, VirtualAxis.scalaPartialVersion("3.3.8")), identity[Project])
  .customRow(
    true,
    Seq(VirtualAxis.jvm, VirtualAxis.scalaVersionAxis("2.13.17", "2.13.17")),
    identity[Project],
  )
  // js and native rows so the groups they share with the JVM exist; the plugins are not needed
  // to name a source tree
  .customRow(true, Seq("3.9.0"), Seq(VirtualAxis.js), _.settings(platform := "sjs1"))
  .customRow(true, Seq("3.9.0"), Seq(VirtualAxis.native), _.settings(platform := "native0.5"))
