val foo = taskKey[Int]("foo")
foo := {
  val _ = (foo / fileInputs).value
  1
}
foo / fileInputs += baseDirectory.value * "foo.txt"
val checkFoo = taskKey[Unit]("check foo inputs")
checkFoo := {
  val actual = (foo / transitiveDependencies).value.toSet
  val expected = (foo / fileInputs).value.toSet
  assert(actual == expected)
}

val bar = taskKey[Int]("bar")
bar := {
  val _ = (bar / fileInputs).value
  foo.value + 1
}
bar / fileInputs += baseDirectory.value * "bar.txt"

val checkBar = taskKey[Unit]("check bar inputs")
checkBar := {
  val actual = (bar / transitiveDependencies).value.toSet
  val expected = ((bar / fileInputs).value ++ (foo / fileInputs).value).toSet
  assert(actual == expected)
}

val baz = taskKey[Int]("baz")
baz / fileInputs += baseDirectory.value * "baz.txt"
baz := {
  println(resolvedScoped.value)
  val _ = (baz / fileInputs).value
  bar.value + 1
}
baz := Def.taskDyn {
  val _ =  (bar / transitiveDependencies).value
  val len = (baz / fileInputs).value.length
  Def.task(bar.value + len)
}.value

val checkBaz = taskKey[Unit]("check bar inputs")
checkBaz := {
  val actual = (baz / transitiveDependencies).value.toSet
  val expected = ((bar / fileInputs).value ++ (foo / fileInputs).value ++ (baz / fileInputs).value).toSet
  assert(actual == expected)
}
