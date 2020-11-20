TaskKey[Unit]("willSucceed") := println("success")

TaskKey[Unit]("willFail") := { throw new Exception("failed") }

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8" % "test"

TaskKey[Unit]("fooBar") := { () }
