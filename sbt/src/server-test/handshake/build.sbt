lazy val root = (project in file("."))
  .settings(
    Global / serverLog / logLevel := Level.Debug,
    name := "handshake",
    scalaVersion := "2.12.3",
  )
