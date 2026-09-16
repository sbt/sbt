// custom is a def, so each call builds a new axis
def custom = VirtualAxis.PlatformAxis("custom", "Custom", "custom")

lazy val core = (projectMatrix in file("core"))
  .customRow(Seq("2.13.18"), Seq(custom), Nil)

lazy val app = (projectMatrix in file("app"))
  .customRow(Seq("2.13.18"), Seq(custom), Nil)
  .dependsOn(core)
