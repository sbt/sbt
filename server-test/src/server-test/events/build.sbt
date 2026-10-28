
commands += Command.command("hello") { state => ??? }

Global / cancelable := true

lazy val inner = taskKey[Unit]("Nested evaluation")
lazy val nested = taskKey[Unit]("Wait after a nested evaluation")
lazy val slowInner = taskKey[Unit]("Wait during a nested evaluation")
lazy val during = taskKey[Unit]("Catch a cancelled nested evaluation")
lazy val duringRetry = taskKey[Unit]("Retry cancellation after swallowing an interrupt")

inner := (())

nested := {
  val s = state.value
  val dir = baseDirectory.value
  Project.extract(s).runTask(inner, s)
  IO.touch(dir / "nested-ready")
  try Thread.sleep(30000)
  catch {
    case _: InterruptedException => IO.touch(dir / "nested-interrupted")
  }
}

slowInner := {
  val dir = baseDirectory.value
  IO.touch(dir / "inner-ready")
  try Thread.sleep(30000)
  catch {
    case e: InterruptedException =>
      IO.touch(dir / "inner-interrupted")
      throw e
  }
}

during := {
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

duringRetry := {
  val s = state.value
  val dir = baseDirectory.value
  try Project.extract(s).runTask(slowInner, s)
  catch {
    case _: InterruptedException => IO.touch(dir / "retry-first-interrupted")
    case _: Incomplete           => ()
  }
  while (!(dir / "retry-resume").exists) {
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
