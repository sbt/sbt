package sbt.parse

	import Parser._
	import org.scalacheck._

object JLineTest
{
	def main(args: Array[String])
	{
		import jline.{ConsoleReader,Terminal}
		val reader = new ConsoleReader()
		Terminal.getTerminal.disableEcho()

		val parser = ParserExample.t
		JLineCompletion.installCustomCompletor(reader, parser)
		def loop() {
			val line = reader.readLine("> ")
			if(line ne null) {
				println("Entered '" + line + "'")
				loop()
			}
		}
		loop()
	}
}
object ParserTest extends Properties("Completing Parser")
{
	val wsc = charClass(_.isWhitespace)
	val ws = ( wsc + ) examples(" ")
	val optWs = ( wsc * ) examples("")

	val nested = (token("a1") ~ token("b2")) ~ "c3"
	val nestedDisplay = (token("a1", "<a1>") ~ token("b2", "<b2>")) ~ "c3"

	def p[T](f: T): T = { /*println(f);*/ f }

	def checkSingle(in: String, expect: Completion)(expectDisplay: Completion = expect) =
		( ("token '" + in + "'") |: checkOne(in, nested, expect)) &&
		( ("display '" + in + "'") |: checkOne(in, nestedDisplay, expectDisplay) )
		
	def checkOne(in: String, parser: Parser[_], expect: Completion): Prop =
		p(completions(parser, in)) == Completions.single(expect)

	def checkInvalid(in: String) =
		( ("token '" + in + "'") |: checkInv(in, nested) ) &&
		( ("display '" + in + "'") |: checkInv(in, nestedDisplay) )
	def checkInv(in: String, parser: Parser[_]): Prop =
		p(completions(parser, in)) == Completions.nil
	
	property("nested tokens a") = checkSingle("", Completion.tokenStrict("","a1") )( Completion.displayStrict("<a1>"))
	property("nested tokens a1") = checkSingle("a", Completion.tokenStrict("a","1") )( Completion.displayStrict("<a1>"))
	property("nested tokens a inv") = checkInvalid("b")
	property("nested tokens b") = checkSingle("a1", Completion.tokenStrict("","b2") )( Completion.displayStrict("<b2>"))
	property("nested tokens b2") = checkSingle("a1b", Completion.tokenStrict("b","2") )( Completion.displayStrict("<b2>"))
	property("nested tokens b inv") = checkInvalid("a1a")
	property("nested tokens c") = checkSingle("a1b2", Completion.suggestStrict("c3") )()
	property("nested tokens c3") = checkSingle("a1b2c", Completion.suggestStrict("3"))()
	property("nested tokens c inv") = checkInvalid("a1b2a")
}
object ParserExample
{
	val ws = charClass(_.isWhitespace)+
	val notws = charClass(!_.isWhitespace)+

	val name = token("test")
	val options = (ws ~ token("quick" | "failed" | "new") )*
	val include = (ws ~ token(examples(notws, Set("am", "is", "are", "was", "were") )) )*

	val t = name ~ options ~ include

	// Get completions for some different inputs
	println(completions(t, "te"))
	println(completions(t, "test "))
	println(completions(t, "test w"))

	// Get the parsed result for different inputs
	println(apply(t)("te").resultEmpty)
	println(apply(t)("test").resultEmpty)
	println(apply(t)("test w").resultEmpty)
	println(apply(t)("test was were").resultEmpty)

	def run(n: Int)
	{
		val a = 'a'.id
		val aq = a.?
		val aqn = repeat(aq, min = n, max = n)
		val an = repeat(a, min = n, max = n)
		val ann = aqn ~ an

		def r = apply(ann)("a"*(n*2)).resultEmpty
		println(r.isDefined)
	}
	def run2(n: Int)
	{
		val ab = "ab".?.*
		val r = apply(ab)("a"*n).resultEmpty
		println(r)
	}
}