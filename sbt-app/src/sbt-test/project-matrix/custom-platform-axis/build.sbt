lazy val marks = settingKey[Seq[String]]("what each platform added")
lazy val check = taskKey[Unit]("")

// a build can define its own platform axis
lazy val wasm = VirtualAxis.PlatformAxis("wasm", "Wasm", "wasm")

lazy val core = (projectMatrix in file("core"))
  .settings(marks := Nil)
  .jvmPlatform(Seq("2.13.18"), Seq(check := assert(marks.value == Nil, marks.value.toString)))
  .customRow(
    true,
    Seq("2.13.18"),
    Seq(wasm),
    _.settings(
      platform := "wasm",
      marks += "wasm",
      check := {
        assert(marks.value == Seq("wasm"), marks.value.toString)
        assert(platform.value == "wasm", platform.value)
      },
    ),
  )

lazy val root = (project in file("."))
  .settings(
    check := {
      val ids = core.allProjects().map(_._1.id)
      assert(ids == Seq("core2_13", "coreWasm2_13"), s"rows: $ids")
      // axes with the same three names are the same platform
      assert(VirtualAxis.PlatformAxis("wasm", "Wasm", "wasm") == wasm, "wasm")
      assert(VirtualAxis.PlatformAxis("js", "JS", "js") == VirtualAxis.js, "js")
    },
  )
