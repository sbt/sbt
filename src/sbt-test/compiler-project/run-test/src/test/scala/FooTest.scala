package foo.bar

import org.scalacheck._

object FooTest extends Properties("Foo")
{
	specify("Set", (i: Int) => { try {
		val foo = new Foo
		foo.eval(i.toString) == i
	} catch { case e => e.printStackTrace(); false } 
	})
}