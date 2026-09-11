lazy val check = taskKey[Unit]("")

// no Scala version, so `jvmPlatform` hands the platform axis to `customRow` itself
lazy val app = (projectMatrix in file("app"))
  .jvmPlatform(
    autoScalaLibrary = false,
    scalaVersions = Nil,
    settings = Seq(check := Def.uncached {
      val axes = virtualAxes.value
      assert(axes == Seq(VirtualAxis.jvm), s"axes: $axes")
      val dirs = (Compile / unmanagedSourceDirectories).value.map(_.getName).distinct.sorted
      assert(dirs == Seq("java", "javajvm", "scala", "scalajvm"), s"dirs: $dirs")
    }),
  )
