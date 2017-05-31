import org.scalatest._

package my.scalatest {
  class MyFlatSuite extends FlatSpec {
    "Passing test" should "pass" in {

    }

    "Failing test" should "fail" in {
      sys.error("wah wah")
    }
  }

  class MyInnerSuite(arg: String) extends FlatSpec {
    "Inner passing test" should "pass" in {

    }

    "Inner failing test" should "fail" in {
      sys.error("wah wah")
    }
  }

  class MyNestedSuites extends Suites(new MyInnerSuite("arrrrrg!"))
}