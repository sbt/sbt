package example

import org.specs._

class ExampleSpec extends Specification {
  "An example project" should {
    "implement rational numbers" >> {
      Rational(5) mustEqual Rational(5, 1)
      Rational(1, 10) mustEqual Rational(2, 20)
      (Rational(1, 3) + Rational(5, 15)) mustEqual Rational(4, 6)
    }
  }
}
