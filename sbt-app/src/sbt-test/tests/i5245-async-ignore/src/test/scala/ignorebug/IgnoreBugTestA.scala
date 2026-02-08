package ignorebug

import scala.concurrent.Future
import org.scalatest.AsyncFunSuite

class IgnoreBugTestA extends AsyncFunSuite {

  test("a-succ") {
    Future.successful(succeed)
  }

  ignore("a-ign") {
    ???
  }
}
