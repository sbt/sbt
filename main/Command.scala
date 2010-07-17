/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package sbt

sealed trait Command
{
	def applies: PartialFunction[State, Apply]
}
trait Apply
{
	def help: Seq[(String,String)]
	def run: PartialFunction[Input, State]
}
object Command
{
	def apply(f: PartialFunction[State, Apply]): Command =
		new Command { def applies = f }
		
	def simple(name: String, help: (String, String)*)(f: State => State): Command =
		apply { case s => Apply(help) { case in if in.line == name => f(s) }}
}
object Apply
{
	def apply(h: Seq[(String,String)])(r: PartialFunction[Input, State]): Apply =
		new Apply { def help = h; def run = r }
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