package foo.bar

import java.io.File
import File.{pathSeparator => / }
import scala.io.Source

class Holder { var value: Any = _ }

import scala.tools.nsc.{GenericRunnerSettings, Interpreter, Settings}

class Foo {
	val g = new GenericRunnerSettings(System.err.println)
	val settings = new Settings()
	val loader = getClass.getClassLoader
	settings.classpath.value = classpath("app", loader).getOrElse(error("Error: could not find application classpath"))
	settings.bootclasspath.value = settings.bootclasspath.value + / + classpath("boot", loader).getOrElse(error("Error: could not find boot classpath"))
	val inter = new Interpreter(settings) {
		override protected def parentClassLoader = Foo.this.getClass.getClassLoader
	}
	def eval(code: String): Any = {
		val h = new Holder
		inter.bind("$r_", h.getClass.getName, h)
		val r = inter.interpret("$r_.value = " + code)
		h.value
	}
	
	private def classpath(name: String, loader: ClassLoader) =
		Option(loader.getResource(name + ".class.path")).map { cp =>
			Source.fromURL(cp).mkString
		}
}

object Test
{
	def main(args: Array[String])
	{
		val foo = new Foo
		args.foreach { arg =>  foo.eval(arg) == arg.toInt }
	}
}