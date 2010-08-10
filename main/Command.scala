/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package sbt

import Execute.NodeView
import Completions.noCompletions

trait Command
{
	def applies: State => Option[Apply]
}
trait Apply
{
	def help: Seq[Help]
	def run: Input => Option[State]
	def complete: Input => Completions
}
trait Help
{
	def detail: (Set[String], String)
	def brief: (String, String)
}
object Help
{
	def apply(briefHelp: (String, String), detailedHelp: (Set[String], String) = (Set.empty, "") ): Help = new Help { def detail = detailedHelp; def brief = briefHelp }
}
trait Completions
{
	def candidates: Seq[String]
	def position: Int
}
object Completions
{
	implicit def seqToCompletions(s: Seq[String]): Completions = apply(s : _*)
	def apply(s: String*): Completions = new Completions { def candidates = s; def position = 0 }

	def noCompletions: PartialFunction[Input, Completions] = { case _ => Completions() }
}
object Command
{
	def univ(f: State => Apply): Command = direct(s => Some(f(s)))
	def direct(f: State => Option[Apply]): Command =
		new Command { def applies = f }
	def apply(f: PartialFunction[State, Apply]): Command =
		direct(f.lift)
		
	def simple(name: String, brief: (String, String), detail: String)(f: (Input, State) => State): Command =
		univ( Apply.simple(name, brief, detail)(f) )
}
object Apply
{
	def direct(h: Help*)(c: Input => Completions = noCompletions )(r: Input => Option[State]): Apply =
		new Apply { def help = h; def run = r; def complete = c }

	def apply(h: Help*)(r: PartialFunction[Input, State]): Apply =
		direct(h : _*)( noCompletions )(r.lift)
	/* Currently problematic for apply( Seq(..): _*)() (...)
	* TODO: report bug and uncomment when fixed
	def apply(h: Help*)(complete: PartialFunction[Input, Completions] = noCompletions )(r: PartialFunction[Input, State]): Apply =
		direct(h : _*)( complete orElse noCompletions )(r.lift)*/

	def simple(name: String, brief: (String, String), detail: String)(f: (Input, State) => State): State => Apply =
	{
		val h = Help(brief, (Set(name), detail) )
		s => Apply( h ){ case in if name == in.name => f( in, s) }
	}
}

trait Logged
{
	def log: Logger
}
trait HistoryEnabled
{
	def historyPath: Option[Path]
}
trait Named
{
	def name: String
}
trait Member[Node <: Member[Node]]
{ self: Node =>
	def projectClosure: Seq[Node]
}
trait Tasked
{
	type Task[T] <: AnyRef
	def task(name: String, state: State): Option[Task[State]]
	implicit def taskToNode: NodeView[Task]
	def help: Seq[Help]
}
trait TaskSetup
{
	def maxThreads = Runtime.getRuntime.availableProcessors
	def checkCycles = false
}
final case class Input(line: String, cursor: Option[Int])
{
	lazy val (name, arguments) = line match { case Input.NameRegex(n, a) => (n, a); case _ => (line, "") }
	lazy val splitSpace = (arguments split """\s+""").toSeq
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