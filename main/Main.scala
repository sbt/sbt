/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt

import complete.HistoryCommands
import HistoryCommands.{Start => HistoryPrefix}
import sbt.build.{AggressiveCompile, Build, BuildException, Parse, ParseException}
import scala.annotation.tailrec

/** This class is the entry point for sbt.*/
class xMain extends xsbti.AppMain
{
	final def run(configuration: xsbti.AppConfiguration): xsbti.MainResult =
	{
		import Commands._
		val initialCommands = Seq(help, history, exit, load)
		val state = State( () )( configuration, initialCommands, Set.empty, None, configuration.arguments.map(_.trim).toList, Next.Continue )
		run(state)
	}
		
	@tailrec final def run(state: State): xsbti.MainResult =
	{
		import Next._
		state.next match
		{
			case Continue => run(next(state))
			case Fail => Exit(1)
			case Done => Exit(0)
			case Reload =>
				val app = state.configuration.provider
				new Reboot(app.scalaProvider.version, state.commands, app.id, state.configuration.baseDirectory)
		}
	}
	def next(state: State): State = state.process(process)
	def process(command: String, state: State): State =
	{
		val in = Input(command)
		Commands.applicable(state).flatMap( _.run(in) ).headOption.getOrElse {
			System.err.println("Unknown command '" + command + "'")
			state.fail
		}
	}
	
}


object Commands
{
	def applicable(state: State): Stream[Apply] =
		state.processors.toStream.flatMap(_.applies(state) )
		
	def help = Command.simple("help", ("help", "Displays this help message.")) { s =>
		val message = applicable(s).flatMap(_.help).map { case (a,b) => a + " : " + b }.mkString("\n")
		System.out.println(message)
		s
	}

	def history = Command { case s @ State(p: HistoryEnabled with Logged) =>
		Apply(HistoryCommands.descriptions) {
			case in if in.line startsWith("!") => 
				HistoryCommands(in.line.substring(HistoryPrefix.length).trim, p.historyPath, 500/*JLine.MaxHistorySize*/, p.log.error _) match
				{
					case Some(commands) =>
						commands.foreach(println)  //better to print it than to log it
						(commands ::: s).continue
					case None => s.fail
				}
		}
	}
	
	def helpExit = (TerminateActions.mkString(", "), "Terminates the build.")
	
	def exit = Command { case s => Apply(helpExit :: Nil) {
		case Input(line) if TerminateActions contains line =>
			s.exit(true)
		}
	}
	
	def load = Command { case s => Apply(Nil) {
		case Input(line) if line.startsWith("load") =>
			loadCommand(line, s.configuration) match
			{
				case Right(newValue) =>
					ExitHooks.runExitHooks(s.exitHooks.toSeq)
					s.copy(project = newValue)(exitHooks = Set.empty)
				case Left(e) => e.printStackTrace; System.err.println(e.toString); s.fail // TODO: log instead of print
			}
		}
	}
	
	def loadCommand(line: String, configuration: xsbti.AppConfiguration): Either[Throwable, Any] =
		try { Right( Build( Parse(line)(configuration.baseDirectory), configuration ) ) }
		catch { case e @ (_: ParseException | _: BuildException | _: xsbti.CompileFailed) => Left(e) }
	
	val Exit = "exit"
	val Quit = "quit"
	/** The list of lowercase command names that may be used to terminate the program.*/
	val TerminateActions: Seq[String] = Seq(Exit, Quit)
}