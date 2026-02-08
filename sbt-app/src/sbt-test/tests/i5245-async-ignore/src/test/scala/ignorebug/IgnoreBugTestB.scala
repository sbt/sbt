package ignorebug

import scala.concurrent.Future
import org.scalatest.AsyncFunSuite

class IgnoreBugTestB extends AsyncFunSuite {

  test("b-succ") {
    Future.successful(succeed)
  }

  ignore("b-ign") {
    ???
  }
}
