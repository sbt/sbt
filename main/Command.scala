/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package sbt

import Execute.NodeView

sealed trait Command
{
	def applies: State => Option[Apply]
}
trait Apply
{
	def help: Seq[(String,String)]
	def run: Input => Option[State]
}
object Command
{
	def direct(f: State => Option[Apply]): Command =
		new Command { def applies = f }
	def apply(f: PartialFunction[State, Apply]): Command =
		direct(f.lift)
		
	def simple(name: String, help: (String, String)*)(f: State => State): Command =
		apply { case s => Apply(help) { case in if in.line == name => f(s) }}
}
object Apply
{
	def direct(h: Seq[(String,String)])(r: Input => Option[State]): Apply =
		new Apply { def help = h; def run = r }
		
	def apply(h: Seq[(String,String)])(r: PartialFunction[Input, State]): Apply =
		direct(h)(r.lift)
}

trait Logged
{
	def log: Logger
}
trait HistoryEnabled
{
	def historyPath: Option[Path]
}

final case class Input(line: String)
{
	def name: String = error("TODO")
	def arguments: String = error("TODO")
}

object Next extends Enumeration {
	val Reload, Fail, Done, Continue = Value
}