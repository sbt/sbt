scalaVersion := "3.6.3"

TaskKey[Unit]("willSucceed") := println("success")

TaskKey[Unit]("willFail") := { throw new Exception("failed") }

libraryDependencies += "org.scalameta" %% "munit" % "1.0.4" % Test

TaskKey[Unit]("fooBar") := { () }

// Exercise the forked interactive code path (connectInput + StdoutOutput).
run / fork := true
run / connectInput := true
run / outputStrategy := Some(StdoutOutput)
