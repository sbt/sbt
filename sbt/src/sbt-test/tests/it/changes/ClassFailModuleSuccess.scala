import org.specs2.mutable._

class B extends Specification
{	
  "'hello world' has 11 characters" in {
     "hello world".length must be equalTo(122)
  }
}

object A extends Specification
{
	"this" should {
    "work" in { 1 must_== 1 }
  }
}