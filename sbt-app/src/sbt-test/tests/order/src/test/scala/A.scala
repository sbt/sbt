import org.scalacheck._

object A extends Properties("A") {
	property("Ran second") = Prop.secure(Counter.i == B.value)
}