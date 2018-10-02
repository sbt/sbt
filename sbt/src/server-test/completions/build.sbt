
val hello = taskKey[Unit]("Say hello")

hello := {}

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5"
