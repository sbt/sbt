import scala.util.continuations._

object Example {

	val x =
		reset {
			shift { k: (Int=>Int) =>
				k(k(k(7)))
			} + 1
		} * 2
}