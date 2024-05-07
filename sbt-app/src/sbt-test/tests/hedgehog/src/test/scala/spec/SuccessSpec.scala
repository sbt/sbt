package spec

import hedgehog._
import hedgehog.runner._

object SuccessSpec extends Properties {
  def tests: List[Test] =
    List(
      property("reverse", testReverse),
    )

  def testReverse: Property =
    for {
      xs <- Gen.alpha.list(Range.linear(0, 100)).log("xs")
    } yield xs.reverse.reverse ==== xs

}
