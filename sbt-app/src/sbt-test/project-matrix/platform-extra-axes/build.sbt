lazy val check = taskKey[Unit]("")
lazy val config = ConfigAxis("Config", "-config")

// an axis of the build's own rides along, and the row takes a function
lazy val core = (projectMatrix in file("core"))
  .addPlatforms(VirtualAxis.jvm)("2.13.18")(config)(
    _.settings(check := assert(virtualAxes.value.contains(config), virtualAxes.value.toString))
  )

lazy val wasm = VirtualAxis.PlatformAxis("wasm", "Wasm", "wasm")

// two versions and two platforms, each named on its own
lazy val many = (projectMatrix in file("many"))
  .configurePlatforms(platform := "wasm")(wasm)
  .addPlatforms(VirtualAxis.jvm, wasm)("2.13.18", "3.3.6")(_.settings(check := ()))

lazy val extraSettings = (projectMatrix in file("extraSettings"))
  .addPlatforms(VirtualAxis.jvm)("2.13.18")(config)(_.settings(check := ()))

lazy val cvExtra = (projectMatrix in file("cvExtra"))
  .addPlatforms(VirtualAxis.jvm)(CrossVersion.full, "2.13.18")(config)(_.settings(check := ()))

lazy val axesExtra = (projectMatrix in file("axesExtra"))
  .addPlatforms(VirtualAxis.jvm)(CrossVersionAxes.abi("2.13.18"), config)(_.settings(check := ()))

lazy val primary = (projectMatrix in file("primary"))
  .addCrossVersionPlatforms(
    CrossVersionAxes.abi("2.13.18"),
    Seq(VirtualAxis.jvm),
    Seq(config),
  )(_.settings(check := ()))

lazy val byCv = (projectMatrix in file("byCv"))
  .addPlatformsBy(VirtualAxis.jvm)(CrossVersion.full, "2.13.18")((_, v) => _.settings(check := ()))

lazy val byExtra = (projectMatrix in file("byExtra"))
  .addPlatformsBy(VirtualAxis.jvm)("2.13.18")(config)((_, v) => _.settings(check := ()))

lazy val byAxes = (projectMatrix in file("byAxes"))
  .addPlatformsBy(VirtualAxis.jvm)(CrossVersionAxes.abi("2.13.18"), config)((_, v) =>
    _.settings(check := ())
  )

// the row function can read its platform as well as its version
lazy val pair = (projectMatrix in file("pair"))
  .configurePlatforms(platform := "wasm")(wasm)
  .addPlatformsBy(VirtualAxis.jvm, wasm)("2.13.18")((p, v) =>
    _.settings(
      check := {
        val want = if p == wasm then "wasm" else "jvm"
        assert(platform.value == want, platform.value)
        assert(v.scalaVersion == "2.13.18", v.scalaVersion)
      }
    )
  )

lazy val root = (project in file("."))
  .settings(
    check := {
      val matrices =
        Seq(core, many, extraSettings, cvExtra, axesExtra, primary, byCv, byExtra, byAxes, pair)
      val ids = matrices.flatMap(_.allProjects().map(_._1.id))
      val named = Seq("coreConfig2_13", "many2_13", "many", "manyWasm2_13", "manyWasm",
        "extraSettingsConfig2_13", "cvExtraConfig2_13_18", "axesExtraConfig2_13", "primaryConfig2_13",
        "byCv2_13_18",
        "byExtraConfig2_13", "byAxesConfig2_13", "pair2_13", "pairWasm2_13")
      assert(ids == named, s"rows: $ids")
    },
  )
