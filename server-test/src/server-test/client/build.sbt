scalaVersion := "3.8.4"

TaskKey[Unit]("willSucceed") := println("success")

TaskKey[Unit]("willFail") := { throw new Exception("failed") }

libraryDependencies += "org.scalameta" %% "munit" % "1.0.4" % Test

TaskKey[Unit]("fooBar") := { () }

commands += Command.command("slowTask") { state =>
  Thread.sleep(10000)
  state
}

TaskKey[Unit]("markLoaded") := IO.touch(baseDirectory.value / "loaded.txt")
