package foo.bar

import java.io.File
import java.net.{URISyntaxException, URL}

class Holder { var value: Any = _ }

import scala.tools.nsc.{Interpreter, Settings}

class Foo {
	val settings = new Settings()
	settings.classpath.value = location(classOf[Holder])
	val inter = new Interpreter(settings)

	def eval(code: String): Any = {
		val h = new Holder
		inter.bind("$r_", h.getClass.getName, h)
		val r = inter.interpret("$r_.value = " + code)
		h.value
	}
	def location(c: Class[_]) =  toFile(c.getProtectionDomain.getCodeSource.getLocation).getAbsolutePath
	def toFile(url: URL) =
		try { new File(url.toURI) }
		catch { case _: URISyntaxException => new File(url.getPath) }
}

object Test
{
	def main(args: Array[String])
	{
		val foo = new Foo
		args.foreach { arg =>  foo.eval(arg) == arg.toInt }
	}
}
