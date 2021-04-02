import complete.{ Completion, Completions, DefaultParsers, Parser }
import DefaultParsers._
import Command.applyEffect
import CommandUtil._

lazy val root = (project in file(".")).
  enablePlugins(FooPlugin).
  settings(
    commands += checkCompletionsCommand
  )

// This checks the tab completion lists build-level keys
def checkCompletionsCommand = Command.make("checkCompletions")(completionsParser)
def completionsParser(state: State) =
  {
    val notQuoted = (NotQuoted ~ any.*) map { case (nq, s) => (nq +: s).mkString }
    val quotedOrUnquotedSingleArgument = Space ~> (StringVerbatim | StringEscapable | notQuoted)
    applyEffect(token(quotedOrUnquotedSingleArgument ?? "" examples ("", " ")))(runCompletions(state))
  }
def runCompletions(state: State)(input: String): State = {
  val xs = Parser.completions(state.combinedParser, input, 9).get map {
    c => if (c.isEmpty) input else input + c.append
  } map { c =>
    c.replaceAll("\n", " ")
  }
  println(xs)
  assert(xs == Set("myTask"))
  state
}
