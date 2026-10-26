
commands += Command.command("hello") { state => ??? }

val blockForever = inputKey[Unit]("A task that blocks forever, used to test cancellation")
blockForever := {
  try Thread.sleep(Long.MaxValue)
  catch { case _: InterruptedException => () }
}

Global / cancelable := true

@transient
lazy val inner = taskKey[Unit]("Nested evaluation")
@transient
lazy val nested = taskKey[Unit]("Wait after a nested evaluation")
@transient
lazy val slowInner = taskKey[Unit]("Wait during a nested evaluation")
@transient
lazy val during = taskKey[Unit]("Catch a cancelled nested evaluation")

inner := Def.uncached(())

nested := Def.uncached {
  val s = state.value
  val dir = baseDirectory.value
  Project.extract(s).runTask(inner, s)
  IO.touch(dir / "nested-ready")
  try Thread.sleep(30000)
  catch {
    case _: InterruptedException => IO.touch(dir / "nested-interrupted")
  }
}

slowInner := Def.uncached {
  val dir = baseDirectory.value
  IO.touch(dir / "inner-ready")
  try Thread.sleep(30000)
  catch {
    case e: InterruptedException =>
      IO.touch(dir / "inner-interrupted")
      throw e
  }
}

during := Def.uncached {
  val s = state.value
  val dir = baseDirectory.value
  try {
    try Project.extract(s).runTask(slowInner, s)
    catch { case _: Incomplete => () }
    Thread.sleep(30000)
    IO.touch(dir / "outer-completed")
  } catch {
    case _: InterruptedException => IO.touch(dir / "outer-interrupted")
  }
}

@transient
lazy val duringRetry = taskKey[Unit]("Retry cancellation after swallowing an interrupt")

duringRetry := Def.uncached {
  val s = state.value
  val dir = baseDirectory.value
  try Project.extract(s).runTask(slowInner, s)
  catch {
    case _: InterruptedException => IO.touch(dir / "retry-first-interrupted")
    case _: Incomplete => ()
  }
  while !(dir / "retry-resume").exists do {
    try Thread.sleep(10)
    catch {
      case _: InterruptedException => IO.touch(dir / "retry-first-interrupted")
    }
  }
  Thread.interrupted()
  IO.touch(dir / "retry-ready")
  try Thread.sleep(30000)
  catch {
    case _: InterruptedException => IO.touch(dir / "retry-interrupted")
  }
}
