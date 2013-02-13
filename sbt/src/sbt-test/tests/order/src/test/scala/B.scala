import org.scalacheck._

object B extends Properties("B") {
	val value = 3
	property("Succeed") = Prop.secure {
		Counter.i = value
		true
	}
}