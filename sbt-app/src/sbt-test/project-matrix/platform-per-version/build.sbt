lazy val check = taskKey[Unit]("")

// a platform of the build's own
lazy val wasm = VirtualAxis.PlatformAxis("wasm", "Wasm", "wasm")

// one call, a row per version and platform, with the settings of a row built from its version
lazy val core = (projectMatrix in file("core"))
  .configurePlatforms(platform := "wasm")(wasm)
  .addPlatformsBy(VirtualAxis.jvm, wasm)("2.13.18", "3.3.6")((_, v) =>
    _.settings(check := assert(scalaVersion.value == v.scalaVersion, scalaVersion.value))
  )

lazy val root = (project in file("."))
  .settings(
    check := {
      val ids = core.allProjects().map(_._1.id).sorted
      assert(ids == Seq("core", "core2_13", "coreWasm", "coreWasm2_13"), s"rows: $ids")
      val libIds = lib.allProjects().map(_._1.id).sorted
      assert(libIds == Seq("lib", "lib2_13", "libWasm2_13"), s"rows: $libIds")
      val appIds = app.allProjects().map(_._1.id).sorted
      assert(appIds == Seq("app", "app2_13"), s"rows: $appIds")
      assert(noVersion.allProjects().isEmpty, s"rows: ${noVersion.allProjects()}")
      assert(noPlatform.allProjects().isEmpty, s"rows: ${noPlatform.allProjects()}")
    },
  )

// a platform is asked which versions it builds for
lazy val lib = (projectMatrix in file("lib"))
  .configurePlatforms(platform := "wasm")(wasm)
  .addPlatformsBy(VirtualAxis.jvm, wasm)(
    p => if p == wasm then Seq("2.13.18") else Seq("2.13.18", "3.3.6")
  )((_, v) => _.settings(check := assert(scalaVersion.value == v.scalaVersion, scalaVersion.value)))

// a call without a version, or without a platform, adds no rows
lazy val noVersion = (projectMatrix in file("noVersion"))
  .addPlatformsBy(VirtualAxis.jvm)(CrossVersionAxes())((_, v) => identity[Project])
lazy val noPlatform = (projectMatrix in file("noPlatform")).addPlatforms()("2.13.18")

// a row is handed to the function, so it can name what only that version needs
lazy val app = (projectMatrix in file("app")).addPlatformsBy(VirtualAxis.jvm)(
  "2.13.18",
  "3.3.6",
)((_, v) =>
  _.settings(check := assert(scalaVersion.value == v.scalaVersion, scalaVersion.value))
    .settings(name := s"app-${v.scalaVersion}")
)
