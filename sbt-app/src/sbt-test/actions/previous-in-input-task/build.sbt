import sjsonnew.BasicJsonProtocol._

val cacheTask = taskKey[Int]("task")
cacheTask := Def.uncached(1)

val checkTask = inputKey[Unit]("validate that the correct value is set by cacheTask")
checkTask := {
  val expected = Def.spaceDelimited("").parsed.head.toInt
  assert(cacheTask.previous.getOrElse(0) == expected)
}
