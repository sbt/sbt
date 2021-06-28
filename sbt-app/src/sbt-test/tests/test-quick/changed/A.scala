case class A(b: B) {
  def foo = b.foo
  // A comment added should trigger recompilation.
}