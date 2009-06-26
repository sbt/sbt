package foo.bar

class Holder { var value: Any = _ }

import scala.tools.nsc.{Interpreter, Settings}

class Foo {
	val settings = new Settings()
	settings.classpath.value = new java.io.File(classOf[Holder].getProtectionDomain.getCodeSource.getLocation.toURI).getAbsolutePath
	val inter = new Interpreter(settings)

	def eval(code: String): Any = {
		val h = new Holder
		inter.bind("$r_", h.getClass.getName, h)
		val r = inter.interpret("$r_.value = " + code)
		h.value
	}
}

object Test
{
	def main(args: Array[String])
	{
		val foo = new Foo
		foo.eval("3")
	}
}