/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util
package complete

object JLineTest {
  import DefaultParsers._

  val one = "blue" | "green" | "black"
  val two = token("color" ~> Space) ~> token(one)
  val three = token("color" ~> Space) ~> token(ID.examples("blue", "green", "black"))
  val four = token("color" ~> Space) ~> token(ID, "<color name>")

  val num = token(NatBasic)
  val five = (num ~ token("+" | "-") ~ num) <~ token('=') flatMap {
    case a ~ "+" ~ b => token((a + b).toString)
    case a ~ "-" ~ b => token((a - b).toString)
  }

  val parsers = Map("1" -> one, "2" -> two, "3" -> three, "4" -> four, "5" -> five)
  def main(args: Array[String]): Unit = {
    import jline.TerminalFactory
    import jline.console.ConsoleReader
    val reader = new ConsoleReader()
    TerminalFactory.get.init

    val parser = parsers(args(0))
    JLineCompletion.installCustomCompletor(reader, parser)
    def loop(): Unit = {
      val line = reader.readLine("> ")
      if (line ne null) {
        println("Result: " + apply(parser)(line).resultEmpty)
        loop()
      }
    }
    loop()
  }
}

import Parser._
import org.scalacheck._

object ParserTest extends Properties("Completing Parser") {
  import Parsers._
  import DefaultParsers.matches

  val nested = (token("a1") ~ token("b2")) ~ "c3"
  val nestedDisplay = (token("a1", "<a1>") ~ token("b2", "<b2>")) ~ "c3"

  val spacePort = token(Space) ~> Port

  def p[T](f: T): T = { println(f); f }

  def checkSingle(in: String, expect: Completion)(expectDisplay: Completion = expect) =
    (("token '" + in + "'") |: checkOne(in, nested, expect)) &&
      (("display '" + in + "'") |: checkOne(in, nestedDisplay, expectDisplay))

  def checkOne(in: String, parser: Parser[_], expect: Completion): Prop =
    completions(parser, in, 1) == Completions.single(expect)

  def checkAll(in: String, parser: Parser[_], expect: Completions): Prop = {
    val cs = completions(parser, in, 1)
    ("completions: " + cs) |: ("Expected: " + expect) |: (cs == expect: Prop)
  }

  def checkInvalid(in: String) =
    (("token '" + in + "'") |: checkInv(in, nested)) &&
      (("display '" + in + "'") |: checkInv(in, nestedDisplay))

  def checkInv(in: String, parser: Parser[_]): Prop = {
    val cs = completions(parser, in, 1)
    ("completions: " + cs) |: (cs == Completions.nil: Prop)
  }

  property("nested tokens a") =
    checkSingle("", Completion.token("", "a1"))(Completion.displayOnly("<a1>"))
  property("nested tokens a1") =
    checkSingle("a", Completion.token("a", "1"))(Completion.displayOnly("<a1>"))
  property("nested tokens a inv") = checkInvalid("b")
  property("nested tokens b") =
    checkSingle("a1", Completion.token("", "b2"))(Completion.displayOnly("<b2>"))
  property("nested tokens b2") =
    checkSingle("a1b", Completion.token("b", "2"))(Completion.displayOnly("<b2>"))
  property("nested tokens b inv") = checkInvalid("a1a")
  property("nested tokens c") = checkSingle("a1b2", Completion.suggestion("c3"))()
  property("nested tokens c3") = checkSingle("a1b2c", Completion.suggestion("3"))()
  property("nested tokens c inv") = checkInvalid("a1b2a")

  property("suggest space") = checkOne("", spacePort, Completion.token("", " "))
  property("suggest port") = checkOne(" ", spacePort, Completion.displayOnly("<port>"))
  property("no suggest at end") = checkOne("asdf", "asdf", Completion.suggestion(""))
  property("no suggest at token end") = checkOne("asdf", token("asdf"), Completion.suggestion(""))
  property("empty suggest for examples") =
    checkOne("asdf", any.+.examples("asdf", "qwer"), Completion.suggestion(""))
  property("empty suggest for examples token") =
    checkOne("asdf", token(any.+.examples("asdf", "qwer")), Completion.suggestion(""))

  val colors = Set("blue", "green", "red")
  val base = (seen: Seq[String]) => token(ID examples (colors -- seen))
  val sep = token(Space)
  val repeat = repeatDep(base, sep)
  def completionStrings(ss: Set[String]) = Completions(ss map (Completion.token("", _)))

  property("repeatDep no suggestions for bad input") = checkInv(".", repeat)
  property("repeatDep suggest all") = checkAll("", repeat, completionStrings(colors))
  property("repeatDep suggest remaining two") = {
    val first = colors.toSeq.head
    checkAll(first + " ", repeat, completionStrings(colors - first))
  }
  property("repeatDep suggest remaining one") = {
    val take = colors.toSeq.take(2)
    checkAll(take.mkString("", " ", " "), repeat, completionStrings(colors -- take))
  }
  property("repeatDep requires at least one token") = !matches(repeat, "")
  property("repeatDep accepts one token") = matches(repeat, colors.toSeq.head)
  property("repeatDep accepts two tokens") = matches(repeat, colors.toSeq.take(2).mkString(" "))
}
object ParserExample {
  val ws = charClass(_.isWhitespace).+
  val notws = charClass(!_.isWhitespace).+

  val name = token("test")
  val options = (ws ~> token("quick" | "failed" | "new")).*
  val exampleSet = Set("am", "is", "are", "was", "were")
  val include = (ws ~> token(
    examples(notws.string, new FixedSetExamples(exampleSet), exampleSet.size, false)
  )).*

  val t = name ~ options ~ include

  // Get completions for some different inputs
  println(completions(t, "te", 1))
  println(completions(t, "test ", 1))
  println(completions(t, "test w", 1))

  // Get the parsed result for different inputs
  println(apply(t)("te").resultEmpty)
  println(apply(t)("test").resultEmpty)
  println(apply(t)("test w").resultEmpty)
  println(apply(t)("test was were").resultEmpty)

  def run(n: Int): Unit = {
    val a = 'a'.id
    val aq = a.?
    val aqn = repeat(aq, min = n, max = n)
    val an = repeat(a, min = n, max = n)
    val ann = aqn ~ an

    def r = apply(ann)("a" * (n * 2)).resultEmpty
    println(r.isValid)
  }
  def run2(n: Int): Unit = {
    val ab = "ab".?.*
    val r = apply(ab)("a" * n).resultEmpty
    println(r)
  }
}
