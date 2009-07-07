import org.scalacheck._

object BasicTest extends Properties("A basic runnable test")
{
	specify("startsWith", (a: String, b: String) => (a+b).startsWith(a))
}

abstract class AbstractNotATest extends Properties("Not a runnable test")
{
	specify("Fail", (a: Int, b: Int) => false)
}

class ClassNotATest extends Properties("Not a runnable test")
{
	specify("Fail", (a: Int, b: Int) => false)
}

trait TraitNotATest
{ self: Properties =>
	specify("Fail", (a: Int, b: Int) => false)
}