package spec

import hedgehog._
import hedgehog.runner._

object FailureSpec extends Properties {
  def tests: List[Test] =
    List(
      example("test failure", testFailure),
    )

  def testFailure: Result = {
    // expected to fail
    1 ==== 2
  }
}
