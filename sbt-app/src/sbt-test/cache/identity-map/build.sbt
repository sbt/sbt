val example = taskKey[Uncacheable]("example")
val alias = taskKey[Uncacheable]("alias")
val both = taskKey[Unit]("both")
val check = taskKey[Unit]("check")

example := Def.taskDyn {
  Def.task {
    Uncacheable.counter.incrementAndGet()
    new Uncacheable()
  }
}.value

alias := example.value

both := Def.uncached {
  val a = example.value
  val b = alias.value
  assert(a eq b)
}

check := assert(Uncacheable.counter.get == 1, s"counter = ${Uncacheable.counter.get}")
