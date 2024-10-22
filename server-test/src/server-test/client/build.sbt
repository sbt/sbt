TaskKey[Unit]("willSucceed") := println("success")

TaskKey[Unit]("willFail") := { throw new Exception("failed") }

scalaVersion := "2.12.20"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8" % "test"

TaskKey[Unit]("fooBar") := { () }
