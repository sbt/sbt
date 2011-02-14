package example

import org.specs._

class ExampleSpec extends Specification {
  "An example project" should {
    "compare Rationals" >> {
      Rational(5) mustEqual Rational(5, 1)
      Rational(1, 10) mustEqual Rational(2, 20)
    }
    "add Rationals" >> {
      (Rational(1, 3) + Rational(5, 15)) mustEqual Rational(4, 6)
    }
    "mix and match Rationals and Ints" >> {
      (Rational(1, 2) + 1) mustEqual Rational(3, 2)
      (1 + Rational(1, 2)) mustEqual Rational(3, 2)
    }
  }
}
