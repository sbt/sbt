import org.scalatest.FlatSpec

class Test extends FlatSpec {
	val v = Option(System.getenv("tests.max.value")) getOrElse Int.MaxValue
	"A simple equation" should "hold" in {
		assert(Int.MaxValue == v)
	}
}
