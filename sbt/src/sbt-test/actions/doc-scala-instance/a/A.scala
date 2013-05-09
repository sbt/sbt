class A {
  def x = 3
}
class B
object B {
	implicit def bToA(b: B): A = new A
}
