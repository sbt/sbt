import org.specs2.mutable._

class B extends Specification
{
	"this" should {
    "not work" in { 1 must_== 2 }
  }
}

object A extends Specification
{
	"this" should {
    "not work" in { 1 must_== 2 }
  }
}