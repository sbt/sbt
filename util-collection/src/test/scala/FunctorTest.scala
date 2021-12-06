import hedgehog.*
import hedgehog.runner.*
import _root_.sbt.util.Functor

object FunctorTest extends Properties:
  val F = summon[Functor[Option]]

  override def tests: List[Test] =
    List(
      example("None", testNone),
      property("identity", identityProperty),
      property("composition", compositionProperty),
      property("map", mapProperty),
    )

  def testNone: Result =
    Result.assert(F.map(None: Option[Int])(_ + 1) == None)

  def identityProperty: Property =
    for x <- Gen.int(Range.linear(-100, 100)).forAll
    yield F.map(Some(x))(identity) ==== Some(x)

  def mapProperty: Property =
    for
      x <- Gen.int(Range.linear(-100, 100)).forAll
      f <- genFun.forAll
    yield F.map(Some(x))(f) ==== Some(f(x))

  def compositionProperty: Property =
    for
      x <- Gen.int(Range.linear(-100, 100)).forAll
      f <- genFun.forAll
      g <- genFun.forAll
    yield F.map(Some(x))(f compose g) ==== F.map(F.map(Some(x))(g))(f)

  def genFun: Gen[Int => Int] =
    for x <- Gen.int(Range.linear(-100, 100))
    yield (_: Int) + x

end FunctorTest
