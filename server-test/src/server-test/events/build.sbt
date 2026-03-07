
commands += Command.command("hello") { state => ??? }

val blockForever = inputKey[Unit]("A task that blocks forever, used to test cancellation")
blockForever := {
  try Thread.sleep(Long.MaxValue)
  catch { case _: InterruptedException => () }
}

Global / cancelable := true
