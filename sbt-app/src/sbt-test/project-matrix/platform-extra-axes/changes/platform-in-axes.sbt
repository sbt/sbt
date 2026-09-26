lazy val check = taskKey[Unit]("")

// the call adds the platform, so the axes must not hold one
lazy val core = (projectMatrix in file("core"))
  .addPlatforms(VirtualAxis.jvm)("2.13.18")(VirtualAxis.jvm)(identity[Project])
