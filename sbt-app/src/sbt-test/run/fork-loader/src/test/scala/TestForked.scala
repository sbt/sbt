import org.scalacheck._

object TestForked extends Properties("Forked loader") {
	property("Loaded from application loader") = Prop.secure {
		CheckLoader()
		true
	}
}