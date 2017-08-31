package foo.bar

import java.io.File
import File.{pathSeparator => / }
import java.net.{URISyntaxException, URL}

class Holder { var value: Any = _ }

import scala.tools.nsc.{GenericRunnerSettings, Interpreter, Settings}

class Foo {
	val g = new GenericRunnerSettings(System.err.println)
	val settings = new Settings()
	settings.classpath.value = location(classOf[Holder])
	settings.bootclasspath.value = settings.bootclasspath.value + / + location(classOf[Product]) + / + location(classOf[Settings])
	val inter = new Interpreter(settings) {
		override protected def parentClassLoader = Foo.this.getClass.getClassLoader
	}
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

object Test {
	def main(args: Array[String]): Unit = {
		// test that Runtime configuration is included
		Class.forName("org.apache.commons.io.ByteOrderMark")
		
		val foo = new Foo
		args.foreach { arg =>  foo.eval(arg) == arg.toInt }
	}
}
