scalaVersion := "3.8.4"

TaskKey[Unit]("willSucceed") := println("success")

TaskKey[Unit]("willFail") := { throw new Exception("failed") }

libraryDependencies += "org.scalameta" %% "munit" % "1.0.4" % Test

TaskKey[Unit]("fooBar") := { () }

// console's shape: hands a client job to the client, but returns Unit
TaskKey[Unit]("runAsUnit") := Def.uncached { val _ = (Compile / run).toTask("").value }

TaskKey[Unit]("runThenFail") := Def.uncached {
  val _ = (Compile / run).toTask("").value
  throw new Exception("failed")
}

// Exercise the forked interactive code path (connectInput + StdoutOutput).
run / fork := true
run / connectInput := true
run / outputStrategy := Some(StdoutOutput)
