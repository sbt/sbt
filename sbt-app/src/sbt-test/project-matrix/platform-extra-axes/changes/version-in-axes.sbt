lazy val check = taskKey[Unit]("")

// the versions add the scala axis, so the axes must not hold one either
lazy val core = (projectMatrix in file("core"))
  .addPlatforms(VirtualAxis.jvm)("2.13.18")(VirtualAxis.scalaABIVersion("3.3.6"))(identity[Project])
