import scala.util.Try

val checkCount = inputKey[Unit]("check that compile has run a specified number of times")
val checkReloadCount = inputKey[Unit]("check whether the project was reloaded")
val failingTask = taskKey[Unit]("should always fail")
val maybeReload = settingKey[(Int, Boolean) => Watched.Action]("possibly reload")
val resetCount = taskKey[Unit]("reset compile count")
val reloadFile = settingKey[File]("get the current reload file")

checkCount := {
  val expected = Def.spaceDelimited().parsed.head.toInt
  if (Count.get != expected)
    throw new IllegalStateException(s"Expected ${expected} compilation runs, got ${Count.get}")
}

maybeReload := { (_, _) =>
  if (Count.reloadCount(reloadFile.value) == 0) Watched.Reload else Watched.CancelWatch
}

reloadFile := baseDirectory.value / "reload-count"

resetCount := {
  Count.reset()
}

failingTask := {
  throw new IllegalStateException("failed")
}

watchPreWatch := maybeReload.value

checkReloadCount := {
  val expected = Def.spaceDelimited().parsed.head.toInt
  assert(Count.reloadCount(reloadFile.value) == expected)
}

val addReloadShutdownHook = Command.command("addReloadShutdownHook") { state =>
  state.addExitHook {
    val base = Project.extract(state).get(baseDirectory)
    val file = base / "reload-count"
    val currentCount = Try(Count.reloadCount(file)).getOrElse(0)
    IO.write(file, s"${currentCount + 1}".getBytes)
  }
}

commands += addReloadShutdownHook

Compile / compile := {
  Count.increment()
  // Trigger a new build by updating the last modified time
  ((Compile / scalaSource).value / "A.scala").setLastModified(5000)
  (Compile / compile).value
}
