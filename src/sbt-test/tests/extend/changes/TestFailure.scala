
import org.scalacheck._
object TestFailure extends Properties("Success -> Failure")
{
	specify("Always true", (i: Int) => true)
}