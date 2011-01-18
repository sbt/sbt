/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package sbt

	import Execute.NodeView
	import java.io.File
	import Function.untupled
	import parse.Parser

trait NewCommand // to replace Command
{
	type T
	def parser: State => Option[Parser[T]]
	def run: (T, State) => State
}
trait Command
{
	def help: State => Seq[Help]
	def run: (Input, State) => Option[State]
}
trait Help
{
	def detail: (Set[String], String)
	def brief: (String, String)
}
object Help
{
	def apply(briefHelp: (String, String), detailedHelp: (Set[String], String) = (Set.empty, "") ): Help =
		new Help { def detail = detailedHelp; def brief = briefHelp }
}
object Command
{
	val Logged = AttributeKey[Logger]("log")
	val HistoryPath = SettingKey[Option[File]]("history")
	val Analysis = AttributeKey[inc.Analysis]("analysis")
	val Watch = SettingKey[Watched]("continuous-watch")

	def direct(h: Help*)(r: (Input, State) => Option[State]): Command =
		new Command { def help = _ => h; def run = r }

	def apply(h: Help*)(r: PartialFunction[(Input, State), State]): Command =
		direct(h : _*)(untupled(r.lift))

	def simple(name: String, brief: (String, String), detail: String)(f: (Input, State) => State): Command =
	{
		val h = Help(brief, (Set(name), detail) )
		simple(name, h)(f)
	}
	def simple(name: String, help: Help*)(f: (Input, State) => State): Command =
		Command( help: _* ){ case (in, s) if name == in.name => f( in, s) }
}
final case class Input(line: String, cursor: Option[Int])
{
	lazy val (name, arguments) = line match { case Input.NameRegex(n, a) => (n, a); case _ => (line, "") }
	lazy val splitArgs: Seq[String] = if(arguments.isEmpty) Nil else (arguments split """\s+""").toSeq
}
object Input
{
	val NameRegex = """\s*(\p{Punct}+|[\w-]+)\s*(.*)""".r
}

object Next extends Enumeration {
	val Reload, Fail, Done, Continue = Value
}
trait CommandDefinitions
{
	def commands: Seq[Command]
}
trait ReflectedCommands extends CommandDefinitions
{
	def commands = ReflectUtilities.allVals[Command](this).values.toSeq
}