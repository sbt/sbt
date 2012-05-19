/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010, 2011  Mark Harrah
 */
package sbt

	import scala.annotation.tailrec
	import java.io.{File, PrintWriter}

object MainLoop
{
	/** Entry point to run the remaining commands in State with managed global logging.*/
	def runLogged(state: State): xsbti.MainResult =
		runLoggedLoop(state, state.globalLogging.backing)

	/** Run loop that evaluates remaining commands and manages changes to global logging configuration.*/
	@tailrec def runLoggedLoop(state: State, logBacking: GlobalLogBacking): xsbti.MainResult =
		runAndClearLast(state, logBacking) match {
			case ret: Return =>  // delete current and last log files when exiting normally
				logBacking.file.delete()
				deleteLastLog(logBacking) 
				ret.result
			case clear: ClearGlobalLog => // delete previous log file, move current to previous, and start writing to a new file
				deleteLastLog(logBacking)
				runLoggedLoop(clear.state, logBacking.shiftNew())
			case keep: KeepGlobalLog => // make previous log file the current log file
				logBacking.file.delete
				runLoggedLoop(keep.state, logBacking.unshift)
		}
		
	/** Runs the next sequence of commands, cleaning up global logging after any exceptions. */
	def runAndClearLast(state: State, logBacking: GlobalLogBacking): RunNext =
		try
			runWithNewLog(state, logBacking)
		catch {
			case e: xsbti.FullReload =>
				deleteLastLog(logBacking)
				throw e // pass along a reboot request
			case e =>
				System.err.println("sbt appears to be exiting abnormally.\n  The log file for this session is at " + logBacking.file)
				deleteLastLog(logBacking)
				throw e
		}

	/** Deletes the previous global log file. */
	def deleteLastLog(logBacking: GlobalLogBacking): Unit =
		logBacking.last.foreach(_.delete())

	/** Runs the next sequence of commands with global logging in place. */
	def runWithNewLog(state: State, logBacking: GlobalLogBacking): RunNext =
		Using.fileWriter(append = true)(logBacking.file) { writer =>
			val out = new java.io.PrintWriter(writer)
			val loggedState = state.copy(globalLogging = logBacking.newLogger(out, logBacking))
			try run(loggedState) finally out.close()
		}
	sealed trait RunNext
	final class ClearGlobalLog(val state: State) extends RunNext
	final class KeepGlobalLog(val state: State) extends RunNext
	final class Return(val result: xsbti.MainResult) extends RunNext

	/** Runs the next sequence of commands that doesn't require global logging changes.*/
	@tailrec def run(state: State): RunNext =
		state.next match
		{
			case State.Continue => run(next(state))
			case State.ClearGlobalLog => new ClearGlobalLog(state.continue)
			case State.KeepLastLog => new KeepGlobalLog(state.continue)
			case ret: State.Return => new Return(ret.result)
		}

	def next(state: State): State =
		ErrorHandling.wideConvert { state.process(Command.process) } match
		{
			case Right(s) => s
			case Left(t: xsbti.FullReload) => throw t
			case Left(t) => handleException(t, state)
		}

		import ExceptionCategory._

	def handleException(e: Throwable, s: State): State =
		handleException(e, s, s.log)
	def handleException(t: Throwable, s: State, log: Logger): State =
	{
		ExceptionCategory(t) match {
			case AlreadyHandled => ()
			case m: MessageOnly => log.error(m.message)
			case f: Full => logFullException(f.exception, log)
		}
		s.fail
	}
	def logFullException(e: Throwable, log: Logger)
	{
		log.trace(e)
		log.error(ErrorHandling reducedToString e)
		log.error("Use 'last' for the full log.")
	}
}