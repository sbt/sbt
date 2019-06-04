package test

import org.scalatest._

class ReflectionTest extends FlatSpec {
  val foo = new Foo
  foo.setValue(3)
  val newFoo = reflection.Reflection.roundTrip(foo)
  assert(newFoo == foo)
  assert(System.identityHashCode(newFoo) != System.identityHashCode(foo))
}

