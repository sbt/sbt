package sbt.complete

object JLineTest
{
	import DefaultParsers._

	val one = "blue" | "green" | "black"
	val two = token("color" ~> Space) ~> token(one)
	val three = token("color" ~> Space) ~> token(ID.examples("blue", "green", "black"))
	val four = token("color" ~> Space) ~> token(ID, "<color name>")

	val num = token(NatBasic)
	val five = (num ~ token("+" | "-") ~ num) <~ token('=') flatMap {
		case a ~ "+" ~ b => token((a+b).toString)
		case a ~ "-" ~ b => token((a-b).toString)
	}

	val parsers = Map("1" -> one, "2" -> two, "3" -> three, "4" -> four, "5" -> five)
	def main(args: Array[String])
	{
		import jline.{ConsoleReader,Terminal}
		val reader = new ConsoleReader()
		Terminal.getTerminal.disableEcho()

		val parser = parsers(args(0))
		JLineCompletion.installCustomCompletor(reader, parser)
		def loop() {
			val line = reader.readLine("> ")
			if(line ne null) {
				println("Result: " + apply(parser)(line).resultEmpty)
				loop()
			}
		}
		loop()
	}
}

	import Parser._
	import org.scalacheck._

object ParserTest extends Properties("Completing Parser")
{
		import Parsers._

	val nested = (token("a1") ~ token("b2")) ~ "c3"
	val nestedDisplay = (token("a1", "<a1>") ~ token("b2", "<b2>")) ~ "c3"

	val spacePort = (token(Space) ~> Port)

	def p[T](f: T): T = { println(f); f }

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

	property("suggest space") = checkOne("", spacePort, Completion.tokenStrict("", " "))
	property("suggest port") = checkOne(" ", spacePort, Completion.displayStrict("<port>") )
	property("no suggest at end") = checkOne("asdf", "asdf", Completion.suggestStrict(""))
	property("no suggest at token end") = checkOne("asdf", token("asdf"), Completion.suggestStrict(""))
	property("empty suggest for examples") = checkOne("asdf", any.+.examples("asdf", "qwer"), Completion.suggestStrict(""))
	property("empty suggest for examples token") = checkOne("asdf", token(any.+.examples("asdf", "qwer")), Completion.suggestStrict(""))
}
object ParserExample
{
	val ws = charClass(_.isWhitespace)+
	val notws = charClass(!_.isWhitespace)+

	val name = token("test")
	val options = (ws ~> token("quick" | "failed" | "new") )*
	val include = (ws ~> token(examples(notws.string, Set("am", "is", "are", "was", "were") )) )*

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