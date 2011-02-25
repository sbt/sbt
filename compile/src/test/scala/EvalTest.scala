package sbt
package compile

	import org.scalacheck._
	import Prop._
	import scala.tools.nsc.reporters.StoreReporter

object EvalTest extends Properties("eval")
{
	private[this] val reporter = new StoreReporter
	import reporter.{ERROR,Info,Severity}
	private[this] val eval = new Eval(_ => reporter, None)
	
	property("inferred integer") = forAll{ (i: Int) =>
		val result = eval.eval(i.toString)
		(label("Value", result.value) |: (result.value == i)) &&
		(label("Type", result.tpe) |: (result.tpe == IntType)) &&
		(label("Files", result.generated) |: (result.generated.isEmpty))
	}

	property("explicit integer") = forAll{ (i: Int) =>
		val result = eval.eval(i.toString, tpeName = Some(IntType))
		(label("Value", result.value) |: (result.value == i)) &&
		(label("Type", result.tpe) |: (result.tpe == IntType)) &&
		(label("Files", result.generated) |: (result.generated.isEmpty))
	}
	
	property("type mismatch") = forAll{ (i: Int, l: Int) =>
		val line = math.abs(l)
		val src = "mismatch"
		throws(eval.eval(i.toString, tpeName =Some(BooleanType), line = line, srcName = src), classOf[RuntimeException]) &&
		hasErrors(line+1, src)
	}

	property("backed local class") = forAll{ (i: Int) =>
		IO.withTemporaryDirectory { dir =>
			val eval = new Eval(_ => reporter, backing = Some(dir))
			val result = eval.eval(local(i))
			val value = result.value.asInstanceOf[{def i: Int}].i
			(label("Value", value) |: (value == i)) &&
			(label("Type", result.tpe) |: (result.tpe == LocalType)) &&
			(label("Files", result.generated) |: (!result.generated.isEmpty))
		}
	}


	property("explicit import") = forAll(testImport("import math.abs" :: Nil))
	property("wildcard import") = forAll(testImport("import math._" :: Nil))
	property("comma-separated imports") = forAll(testImport("import util._, math._, xml._" :: Nil))
	property("multiple imports") = forAll(testImport("import util._" :: "import math._" :: "import xml._" :: Nil))

	private[this] def testImport(imports: Seq[String]): Int => Prop = i =>
		eval.eval("abs("+i+")", new EvalImports(imports.zipWithIndex, "imp")).value == math.abs(i)

	private[this] def local(i: Int) = "{ class ETest(val i: Int); new ETest(" + i + ") }"
	val LocalType = "java.lang.Object with ScalaObject{val i: Int}"

	private[this] def hasErrors(line: Int, src: String) =
	{
		val is = reporter.infos
		("Has errors" |: (!is.isEmpty)) &&
		all(is.toSeq.map(validPosition(line,src)) :_*)
	}
	private[this] def validPosition(line: Int, src: String)(i: Info) =
	{
		val nme = i.pos.source.file.name
		(label("Severity", i.severity) |: (i.severity == ERROR)) &&
		(label("Line", i.pos.line) |: (i.pos.line == line)) &&
		(label("Name", nme) |: (nme == src))
	}
	val IntType = "Int"
	val BooleanType = "Boolean"

	def label(s: String, value: Any) = s + " (" + value + ")"
}
