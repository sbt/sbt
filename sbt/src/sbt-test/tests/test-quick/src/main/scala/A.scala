// A, B are referring to each other, OK in the same compilation group.
case class A(b: B) {
  def foo = b.foo
}