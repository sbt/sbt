import org.specs._

class A extends Specification
{
	"this" should {
    "work" in { 1 must_== 1 }
  }
}

object A extends Specification
{
	"this" should {
    "not work" in { 1 must_== 2 }
  }
}