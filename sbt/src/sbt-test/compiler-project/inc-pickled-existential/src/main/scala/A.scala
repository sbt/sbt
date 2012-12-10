package test6

trait A {
  object Foo extends Module[Foo[_]]

  class Foo[TResult]

  def b = new B
}
