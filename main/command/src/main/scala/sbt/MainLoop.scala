/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010, 2011  Mark Harrah
 */
package sbt

import scala.annotation.tailrec
import java.io.{ File, PrintWriter }
import jline.TerminalFactory

object MainLoop {
  /** Entry point to run the remaining commands in State with managed global logging.*/
  def runLogged(state: State): xsbti.MainResult = {
    // We've disabled jline shutdown hooks to prevent classloader leaks, and have been careful to always restore
    // the jline terminal in finally blocks, but hitting ctrl+c prevents finally blocks from being executed, in that
    // case the only way to restore the terminal is in a shutdown hook.
    val shutdownHook = new Thread(new Runnable {
      def run(): Unit = TerminalFactory.get().restore()
    })

    try {
      Runtime.getRuntime.addShutdownHook(shutdownHook)
      runLoggedLoop(state, state.globalLogging.backing)
    } finally {
      Runtime.getRuntime.removeShutdownHook(shutdownHook)
    }
  }

  /** Run loop that evaluates remaining commands and manages changes to global logging configuration.*/
  @tailrec def runLoggedLoop(state: State, logBacking: GlobalLogBacking): xsbti.MainResult =
    runAndClearLast(state, logBacking) match {
      case ret: Return => // delete current and last log files when exiting normally
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
      case e: Throwable =>
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
      val newLogging = state.globalLogging.newLogger(out, logBacking)
      transferLevels(state, newLogging)
      val loggedState = state.copy(globalLogging = newLogging)
      try run(loggedState) finally out.close()
    }

  /** Transfers logging and trace levels from the old global loggers to the new ones. */
  private[this] def transferLevels(state: State, logging: GlobalLogging): Unit = {
    val old = state.globalLogging
    Logger.transferLevels(old.backed, logging.backed)
    (old.full, logging.full) match { // well, this is a hack
      case (oldLog: AbstractLogger, newLog: AbstractLogger) => Logger.transferLevels(oldLog, newLog)
      case _ => ()
    }
  }

  sealed trait RunNext
  final class ClearGlobalLog(val state: State) extends RunNext
  final class KeepGlobalLog(val state: State) extends RunNext
  final class Return(val result: xsbti.MainResult) extends RunNext

  /** Runs the next sequence of commands that doesn't require global logging changes.*/
  @tailrec def run(state: State): RunNext =
    state.next match {
      case State.Continue       => run(next(state))
      case State.ClearGlobalLog => new ClearGlobalLog(state.continue)
      case State.KeepLastLog    => new KeepGlobalLog(state.continue)
      case ret: State.Return    => new Return(ret.result)
    }

  def next(state: State): State =
    ErrorHandling.wideConvert { state.process(Command.process) } match {
      case Right(s)                  => s
      case Left(t: xsbti.FullReload) => throw t
      case Left(t)                   => state.handleError(t)
    }

  @deprecated("Use State.handleError", "0.13.0")
  def handleException(e: Throwable, s: State): State = s.handleError(e)

  @deprecated("Use State.handleError", "0.13.0")
  def handleException(t: Throwable, s: State, log: Logger): State = State.handleException(t, s, log)

  def logFullException(e: Throwable, log: Logger): Unit = State.logFullException(e, log)
}
