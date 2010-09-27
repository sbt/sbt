package scalaz

trait Dual[A] {
  val value : A
}

object Dual {
  implicit def dual[A](a: A) = new Dual[A] {
    val value = a
  }
}