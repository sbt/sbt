given Foo[Int] = Foo(2)

given s: Foo[String] = Foo("a")

InputKey[Unit]("check") := {
  assert(summon[Foo[Int]].value == 2)
  assert(summon[Foo[String]].value == "a")
  assert(s.value == "a")
}
