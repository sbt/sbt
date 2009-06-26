
import org.scalacheck._
object TestSuccess extends Properties("Failure -> Success")
{
	specify("Always false", (i: Int) => false)
}