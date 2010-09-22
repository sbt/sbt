import org.specs._

object BasicTest extends Specification
{
  "Test resource on test classpath" in {
     getClass.getResource("TestResource.txt") mustNotBe null
  }
  "Main resource on test classpath" in {
     getClass.getResource("MainResource.txt") mustNotBe null
  }
}