import org.scalatest.FlatSpec
import org.scalatest.matchers.MustMatchers

class Test extends FlatSpec with MustMatchers {
	val v = Option(System.getenv("tests.max.value")) getOrElse Int.MaxValue
	"A simple equation" must "hold" in {
		Int.MaxValue must equal (v)
	}
}
