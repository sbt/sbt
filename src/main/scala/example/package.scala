// The example package object.
package object example {
  // Importing an implicit method of type Int => Rational will
  // henceforth let us use Ints as if they were Rationals.
  implicit def intToRational(num: Int): Rational = new Rational(num)  
}