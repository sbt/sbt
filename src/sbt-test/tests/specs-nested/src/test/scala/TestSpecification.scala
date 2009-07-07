import org.specs._

object TestSpecification extends Specification {
  "A sample specification1" should {
    "return something" in {
      "hello" mustNotBe "world"
    }
  }
  
  object sampleSpecification extends Specification {
    "the first system" should {
      "skip one example" in { skip("skipped") }
      "have one example ok" in {}
      "have one example ko" in { 1 mustBe 2 }
      "have one example in error" in { throw new Error("error") }
    }    
  }

}