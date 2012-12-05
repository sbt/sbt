package demo

import sbt.{Command,State}

object AddNewCommand extends (State => State)
{
	def apply(s: State): State = s ++ Seq(newCommand)

	def newCommand = Command.command("newCommand") { (s: State) =>
		println("This is a new command")
		s
	}
}