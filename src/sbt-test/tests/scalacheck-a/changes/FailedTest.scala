import org.scalacheck._

object BasicTest extends Properties("A basic runnable test")
{
	specify("startsWith", (a: String, b: String) => (a+b).startsWith(a))
}

abstract class AbstractNotATest extends Properties("Not a runnable test")
{
	specify("Fail", (a: Int, b: Int) => false)
}

object ClassNotATest extends Properties("A failing test")
{
	specify("Fail", (a: Int, b: Int) => false)
}

trait TraitNotATest extends Properties("Not a runnable test")
{
	specify("Fail", (a: Int, b: Int) => false)
}