import sjsonnew.BasicJsonProtocol._
val foo = taskKey[Int]("an integer")
foo := 1

val bar = taskKey[Int]("bar")
bar := {
  val current = foo.value
  foo.previousInThis match {
    case Some(v) if v == 2 => throw new IllegalStateException("should be unreachable")
    case Some(v) if v == current => v
    case _ => if (current == 2) throw new IllegalStateException("invalid") else current
  }
}

val baz = taskKey[Int]("baz")
baz := {
  val current = foo.value
  val barV = bar.value
  val fooPrevious = foo.previous
  foo.previousInThis match {
    case Some(v) if v == current => -1
    case Some(v) if fooPrevious.getOrElse(v) == v =>
      throw new IllegalStateException("foo.previous and foo.previousInThis were the same")
    case _ => barV
  }
}

val checkBaz = inputKey[Unit]("check the value of baz")
checkBaz := {
  assert(baz.value == Def.spaceDelimited().parsed.head.toInt)
}