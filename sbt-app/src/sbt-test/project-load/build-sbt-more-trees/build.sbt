extension (a: Int) {
  infix def x1(b: Int): Int = a + b
}

class A(x: Int) extends B {
  def y: Int = x + 1
}

trait B

type MyId[A] = A

type MatchTypeExample[A] = A match {
  case Int => String
  case _ => Int
}

enum Enum1 {
  case A1, A2
}

object C {
  val x = 2
}

InputKey[Unit]("check") := {
  val f: MyId[Int] = 8
  val a = new A(2)
  assert(a.y == 3)
  assert(C.x == 2)
  assert(Enum1.A2.ordinal == 1)
  assert((2 x1 3) == 5)
}
