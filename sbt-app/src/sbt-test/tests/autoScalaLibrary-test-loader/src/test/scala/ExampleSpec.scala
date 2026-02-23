import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ExampleSpec extends AnyFlatSpec with Matchers {
  "test with autoScalaLibrary false" should "use project classpath only" in {
    1 shouldBe 1
  }
}
