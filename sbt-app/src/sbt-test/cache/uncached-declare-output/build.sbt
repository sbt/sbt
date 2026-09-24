/*
 * sbt
 * Copyright 2026, Scala center
 * Licensed under Apache License 2.0 (see LICENSE)
 */

import sbt.internal.util.StringVirtualFile1
import xsbti.VirtualFile

@transient lazy val zero = taskKey[Unit]("")
@transient lazy val one = taskKey[Unit]("")
@transient lazy val many = taskKey[Unit]("")
@transient lazy val nested = taskKey[Unit]("")
@transient lazy val direct = taskKey[VirtualFile]("")
@transient lazy val viaTask = taskKey[Unit]("")
@transient lazy val viaUncachedTask = taskKey[Unit]("")
@transient lazy val viaDynamic = taskKey[Unit]("")
@transient lazy val checkRuns = taskKey[Unit]("")
lazy val optedOut = taskKey[Unit]("")

Global / localCacheDirectory := baseDirectory.value / "diskcache"

zero := {
  val output = StringVirtualFile1("zero.txt", "zero")
  var calls = 0
  val result = Def.declareOutput {
    calls += 1
    output
  }
  assert(result eq output)
  assert(calls == 1)
  if false then Def.declareOutput { calls += 1; output }
  assert(calls == 1)
  val results = List(1, 2, 3).map: _ =>
    Def.declareOutput { calls += 1; output }
  assert(results.forall(_ eq output))
  assert(calls == 4)
  val failure = new RuntimeException("argument failure")
  val thrown = try
    Def.declareOutput(throw failure)
    None
  catch case e: RuntimeException => Some(e)
  assert(thrown.contains(failure))
}

one := {
  val dir = target.value
  val output = StringVirtualFile1("one.txt", "one")
  val result = Def.declareOutput(output)
  assert(result eq output)
  assert(dir.getName.nonEmpty)
}

many := {
  val converter = fileConverter.value
  val dir = target.value
  val output = converter.toVirtualFile((dir / "many.txt").toPath)
  assert(Def.declareOutput(output) eq output)
  val counter = dir / "runs"
  val previous = if counter.exists then IO.read(counter).toInt else 0
  IO.write(counter, (previous + 1).toString)
}

direct := StringVirtualFile1("direct.txt", "direct")

nested := {
  val result = Def.declareOutput(direct.value)
  assert(result.id == "direct.txt")
  val converted = Def.declareOutput(fileConverter.value.toVirtualFile(target.value.toPath))
  assert(converted.id.nonEmpty)
  val output = StringVirtualFile1("nested.txt", "nested")
  assert(Def.declareOutput(Def.declareOutput(output)) eq output)
}

optedOut := Def.uncached {
  val output = StringVirtualFile1("opted-out.txt", "opted-out")
  assert(Def.declareOutput(output) eq output)
}

viaTask := Def.task {
  val output = StringVirtualFile1("task.txt", "task")
  assert(Def.declareOutput(output) eq output)
}.value

viaUncachedTask := Def.uncachedTask {
  val output = StringVirtualFile1("uncached-task.txt", "uncached-task")
  assert(Def.declareOutput(output) eq output)
}.value

viaDynamic := Def.taskDyn {
  val output = StringVirtualFile1("dynamic.txt", "dynamic")
  assert(Def.declareOutput(output) eq output)
  Def.task(())
}.value

checkRuns := assert(IO.read(target.value / "runs") == "2")
