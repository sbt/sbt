import org.specs2.mutable._

object BasicTest extends Specification
{
  "Test resource on test classpath" in {
     getClass.getResource("TestResource.txt") must not beNull
  }
  "Main resource on test classpath" in {
     getClass.getResource("MainResource.txt") must not beNull
  }
}