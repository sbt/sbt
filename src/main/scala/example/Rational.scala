package example

// Primary constructor takes two arguments.
class Rational(n: Int, d: Int) {
  // an auxiliary constructor: must call the primary constructor first.
  def this(n: Int) = this(n, 1)
  // A requirement: an exception is thrown upon construction if the condition is false.
  require(d > 0, "denominator must be greater than zero")
  
  // A grossly inefficient greatest common divisor, for illustrative purposes only.
  private def gcd(n: Int, d: Int) = (
    n max d to 2 by -1 find (g => n % g == 0 && d % g == 0) getOrElse 1
  )
  // Using the gcd to calculate reduced numerator and denominator.
  private val g = gcd(n, d)
  // Public, immutable values.
  val numerator   = n / g
  val denominator = d / g
  
  // Assume we have r1: Rational, r2: Rational, and num: Int.
  // Since + is a method like any other, if we define it with a
  // Rational argument then r1 + r2 is defined.
  def +(that: Rational): Rational = new Rational(
    this.numerator * that.denominator + that.numerator * this.denominator,
    this.denominator * that.denominator
  )
  // You can overload the + method with an Int argument: now r1 + num
  // is also defined.  However to make num + r1 work similarly requires
  // an implicit conversion.  (See the example package object.)
  def +(that: Int): Rational = this + new Rational(that)
  
  // toString, equals, and hashCode all override methods in AnyRef
  // so "override" is required.
  override def toString = n + "/" + d + (
    // The result of the if/else is a String:
    if (numerator == n) ""                          // the empty string if it is irreducible
    else " (" + numerator + "/" + denominator + ")" // the reduced form otherwise
  )
  
  // To preserve symmetry we will be equal only to other Rationals.
  override def equals(other: Any) = other match {
    case x: Rational  => this.numerator == x.numerator && this.denominator == x.denominator
    case _            => false
  }
  // As with java, equals and hashCode should always be overridden together.
  override def hashCode = numerator.## + denominator.##  
}

// The Rational companion object.
object Rational {
  // A factory method on the companion object allows construction
  // without explicit calls to new.  Here, d is given a default argument
  // of 1.  This is an alternative mechanism to the auxiliary constructor.
  // used in the class.
  def apply(n: Int, d: Int = 1): Rational = new Rational(n, d)
}