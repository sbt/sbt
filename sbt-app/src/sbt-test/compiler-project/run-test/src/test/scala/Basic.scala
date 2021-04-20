package foo.bar

import org.junit._
import org.junit.Assert._

class Basic
{
	val foo = new Foo
	@Test
	def checkBind(): Unit =
	{
		try { assertTrue( foo.eval("3") == 3) }
		catch { case e => e.printStackTrace; throw e}
	}
}
