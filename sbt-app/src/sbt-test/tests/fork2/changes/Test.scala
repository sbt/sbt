import org.scalatest.FlatSpec

class Test extends FlatSpec {
	val v = sys.env.getOrElse("tests.max.value", Int.MaxValue)
	"A simple equation" should "hold" in {
		assert(Int.MaxValue == v)
	}
}
