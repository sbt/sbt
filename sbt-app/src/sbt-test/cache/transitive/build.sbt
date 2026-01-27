import sbt.internal.util.StringVirtualFile1

val task2 = taskKey[Unit]("Upstream cached task that produces t2.txt")
val task1 = taskKey[Unit]("Downstream cached task that depends on task2 and produces t1.txt")
val checkTask = taskKey[Unit]("Check that files exist")

Global / localCacheDirectory := baseDirectory.value / "diskcache"

task2 := {
  val output = StringVirtualFile1("${OUT}/t2.txt", "task2 output")
  Def.declareOutput(output)
  ()
}

task1 := {
  task2.value
  val output = StringVirtualFile1("${OUT}/t1.txt", "task1 output")
  Def.declareOutput(output)
  ()
}

checkTask := Def.uncached {
  val t1File = baseDirectory.value / "target" / "out" / "t1.txt"
  val t2File = baseDirectory.value / "target" / "out" / "t2.txt"
  assert(t1File.exists(), s"t1.txt should exist")
  assert(t2File.exists(), s"t2.txt should exist")
}
