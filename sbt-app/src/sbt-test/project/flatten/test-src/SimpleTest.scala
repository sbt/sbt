  import org.scalacheck._
  import Prop._

class SimpleTest extends Properties("Simple") {
  property("increment scala") = forAll( (i: Int) => (new a.b.ScalaA).increment(i) == i+1)
  property("increment java") = forAll( (i: Int) => (new JavaA).inc(i) == i+1)

  // property("decrement scala") = forAll( (i: Int) => (new b.ScalaB).decrement(i) == i+1)
  // property("decrement java") = forAll( (i: Int) => (new a.JavaB).dec(i) == i+1)
}
object MainTest {
  def main(args: Array[String]): Unit = ()
}
