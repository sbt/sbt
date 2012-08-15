import org.scalatest.FlatSpec
import org.scalatest.matchers.MustMatchers

class Test extends FlatSpec with MustMatchers {
	"A simple equation" must "hold" in {
		Int.MaxValue must equal (Int.MaxValue)
	}
}
