scalaVersion := "3.8.4"

Global / serverConnectionType := ConnectionType.Tcp
Global / serverPort := 5002

lazy val root = (project in file("."))
  .settings(
    name := "tcp",
  )
