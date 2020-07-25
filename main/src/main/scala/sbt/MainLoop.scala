/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.PrintWriter
import java.util.Properties

import sbt.BasicCommandStrings.{ StashOnFailure, networkExecPrefix }
import sbt.internal.ShutdownHooks
import sbt.internal.langserver.ErrorCodes
import sbt.internal.protocol.JsonRpcResponseError
import sbt.internal.nio.CheckBuildSources.CheckBuildSourcesKey
import sbt.internal.util.{ ErrorHandling, GlobalLogBacking, Prompt, Terminal }
import sbt.internal.{ ShutdownHooks, TaskProgress }
import sbt.io.{ IO, Using }
import sbt.protocol._
import sbt.util.{ Logger, LoggerContext }

import scala.annotation.tailrec
import scala.util.control.NonFatal
import sbt.internal.FastTrackCommands

object MainLoop {

  /** Entry point to run the remaining commands in State with managed global logging.*/
  def runLogged(state: State): xsbti.MainResult = {

    // This warns if ~/.coursier/cache is found.
    // Temporary, remove when updating coursier to 2.0.0 final.
    lmcoursier.CoursierConfiguration.checkLegacyCache()

    // We've disabled jline shutdown hooks to prevent classloader leaks, and have been careful to always restore
    // the jline terminal in finally blocks, but hitting ctrl+c prevents finally blocks from being executed, in that
    // case the only way to restore the terminal is in a shutdown hook.
    val shutdownHook = ShutdownHooks.add(Terminal.restore)

    try {
      runLoggedLoop(state, state.globalLogging.backing)
    } finally {
      shutdownHook.close()
      ()
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
  def runAndClearLast(state: State, logBacking: GlobalLogBacking): RunNext = {
    try runWithNewLog(state, logBacking)
    catch {
      case e: xsbti.FullReload =>
        deleteLastLog(logBacking)
        throw e // pass along a reboot request
      case e: RebootCurrent =>
        deleteLastLog(logBacking)
        deleteCurrentArtifacts(state)
        throw new xsbti.FullReload(e.arguments.toArray, false)
      case NonFatal(e) =>
        System.err.println(
          "sbt appears to be exiting abnormally.\n  The log file for this session is at " + logBacking.file
        )
        deleteLastLog(logBacking)
        throw e
    }
  }

  /** Deletes the previous global log file. */
  def deleteLastLog(logBacking: GlobalLogBacking): Unit =
    logBacking.last.foreach(_.delete())

  /** Deletes the current sbt artifacts from boot. */
  private[sbt] def deleteCurrentArtifacts(state: State): Unit = {
    import sbt.io.syntax._
    val provider = state.configuration.provider
    val appId = provider.id
    // If we can obtain boot directory more accurately it'd be better.
    val defaultBoot = BuildPaths.defaultGlobalBase / "boot"
    val buildProps = state.baseDir / "project" / "build.properties"
    // First try reading the sbt version from build.properties file.
    val sbtVersionOpt = if (buildProps.exists) {
      val buildProperties = new Properties()
      IO.load(buildProperties, buildProps)
      Option(buildProperties.getProperty("sbt.version"))
    } else None
    val sbtVersion = sbtVersionOpt.getOrElse(appId.version)
    val currentArtDirs = defaultBoot * "*" / appId.groupID / appId.name / sbtVersion
    currentArtDirs.get foreach { dir =>
      state.log.info(s"deleting $dir")
      IO.delete(dir)
    }
  }

  /** Runs the next sequence of commands with global logging in place. */
  def runWithNewLog(state: State, logBacking: GlobalLogBacking): RunNext = {
    Using.fileWriter(append = true)(logBacking.file) { writer =>
      val out = new PrintWriter(writer)
      val full = state.globalLogging.full
      val newLogging =
        state.globalLogging.newAppender(full, out, logBacking, LoggerContext.globalContext)
      // transferLevels(state, newLogging)
      val loggedState = state.copy(globalLogging = newLogging)
      try run(loggedState)
      finally {
        out.close()
      }
    }
  }

  // /** Transfers logging and trace levels from the old global loggers to the new ones. */
  // private[this] def transferLevels(state: State, logging: GlobalLogging): Unit = {
  //   val old = state.globalLogging
  //   Logger.transferLevels(old.backed, logging.backed)
  //   (old.full, logging.full) match { // well, this is a hack
  //     case (oldLog: AbstractLogger, newLog: AbstractLogger) => Logger.transferLevels(oldLog, newLog)
  //     case _                                                => ()
  //   }
  // }

  sealed trait RunNext
  final class ClearGlobalLog(val state: State) extends RunNext
  final class KeepGlobalLog(val state: State) extends RunNext
  final class Return(val result: xsbti.MainResult) extends RunNext

  /** Runs the next sequence of commands that doesn't require global logging changes. */
  @tailrec def run(state: State): RunNext =
    state.next match {
      case State.Continue       => run(next(state))
      case State.ClearGlobalLog => new ClearGlobalLog(state.continue)
      case State.KeepLastLog    => new KeepGlobalLog(state.continue)
      case ret: State.Return    => new Return(ret.result)
    }

  def next(state: State): State = {
    val context = LoggerContext(useLog4J = state.get(Keys.useLog4J.key).getOrElse(false))
    val taskProgress = new TaskProgress
    try {
      ErrorHandling.wideConvert {
        state
          .put(Keys.loggerContext, context)
          .put(Keys.taskProgress, taskProgress)
          .process(processCommand)
      } match {
        case Right(s)                  => s.remove(Keys.loggerContext)
        case Left(t: xsbti.FullReload) => throw t
        case Left(t: RebootCurrent)    => throw t
        case Left(t)                   => state.remove(Keys.loggerContext).handleError(t)
      }
    } catch {
      case oom: OutOfMemoryError
          if oom.getMessage != null && oom.getMessage.contains("Metaspace") =>
        System.gc() // Since we're under memory pressure, see if more can be freed with a manual gc.
        val isTestOrRun = state.remainingCommands.headOption.exists { exec =>
          val cmd = exec.commandLine
          cmd.contains("test") || cmd.contains("run")
        }
        val isConsole = state.remainingCommands.exists(_.commandLine == "shell") ||
          (state.remainingCommands.last.commandLine == "iflast shell")
        val testOrRunMessage =
          if (!isTestOrRun) ""
          else
            " If this error occurred during a test or run evaluation, it can be caused by the " +
              "choice of ClassLoaderLayeringStrategy. Of the available strategies, " +
              "ClassLoaderLayeringStrategy.ScalaLibrary will typically use the least metaspace. " +
              (if (isConsole)
                 " To change the layering strategy for this session, run:\n\n" +
                   "set ThisBuild / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy." +
                   "ScalaLibrary"
               else "")
        val msg: String =
          s"Caught $oom\nTo best utilize classloader caching and to prevent file handle leaks, we " +
            s"recommend running sbt without a MaxMetaspaceSize limit. $testOrRunMessage"
        state.log.error(msg)
        state.log.error("\n")
        state.handleError(oom)
    } finally {
      context.close()
      taskProgress.close()
    }
  }

  /** This is the main function State transfer function of the sbt command processing. */
  def processCommand(exec: Exec, state: State): State = {
    val channelName = exec.source map (_.channelName)
    val exchange = StandardMain.exchange
    exchange notifyStatus
      ExecStatusEvent("Processing", channelName, exec.execId, Vector())
    try {
      def process(): State = {
        val progressState = state.get(sbt.Keys.currentTaskProgress) match {
          case Some(_) => state
          case _ =>
            if (state.get(Keys.stateBuildStructure).isDefined) {
              val extracted = Project.extract(state)
              val progress = EvaluateTask.executeProgress(extracted, extracted.structure, state)
              state.put(sbt.Keys.currentTaskProgress, new Keys.TaskProgress(progress))
            } else state
        }
        exchange.setState(progressState)
        exchange.setExec(Some(exec))
        val restoreTerminal = channelName.flatMap(exchange.channelForName) match {
          case Some(c) =>
            val prevTerminal = Terminal.set(c.terminal)
            val prevPrompt = c.terminal.prompt
            // temporarily set the prompt to running during task evaluation
            c.terminal.setPrompt(Prompt.Running)
            () => {
              c.terminal.setPrompt(prevPrompt)
              Terminal.set(prevTerminal)
              c.terminal.setPrompt(prevPrompt)
              c.terminal.flush()
            }
          case _ => () => ()
        }
        /*
         * FastTrackCommands.evaluate can be significantly faster than Command.process because
         * it avoids an expensive parsing step for internal commands that are easy to parse.
         * Dropping (FastTrackCommands.evaluate ... getOrElse) should be functionally identical
         * but slower.
         */
        val newState = try {
          FastTrackCommands
            .evaluate(progressState, exec.commandLine)
            .getOrElse(Command.process(exec.commandLine, progressState))
        } finally {
          // Flush the terminal output after command evaluation to ensure that all output
          // is displayed in the thin client before we report the command status. Also
          // set the promt to whatever it was before we started evaluating the task.
          restoreTerminal()
        }
        if (exec.execId.fold(true)(!_.startsWith(networkExecPrefix)) &&
            !exec.commandLine.startsWith(networkExecPrefix)) {
          val doneEvent = ExecStatusEvent(
            "Done",
            channelName,
            exec.execId,
            newState.remainingCommands.toVector map (_.commandLine),
            exitCode(newState, state),
          )
          exchange.respondStatus(doneEvent)
        }
        exchange.setExec(None)
        newState.get(sbt.Keys.currentTaskProgress).foreach(_.progress.stop())
        newState.remove(sbt.Keys.currentTaskProgress)
      }
      state.get(CheckBuildSourcesKey) match {
        case Some(cbs) =>
          if (!cbs.needsReload(state, exec)) process()
          else Exec("reload", None) +: exec +: state.remove(CheckBuildSourcesKey)
        case _ => process()
      }
    } catch {
      case err: JsonRpcResponseError =>
        exchange.respondError(err, exec.execId, channelName.map(CommandSource(_)))
        throw err
      case err: Throwable =>
        val errorEvent = ExecStatusEvent(
          "Error",
          channelName,
          exec.execId,
          Vector(),
          ExitCode(ErrorCodes.UnknownError),
          Option(err.getMessage),
        )
        StandardMain.exchange.respondStatus(errorEvent)
        throw err
    }
  }

  def logFullException(e: Throwable, log: Logger): Unit = State.logFullException(e, log)

  private[this] type ExitCode = Option[Long]
  private[this] object ExitCode {
    def apply(n: Long): ExitCode = Option(n)
    val Success: ExitCode = ExitCode(0)
    val Unknown: ExitCode = None
  }

  private[this] def exitCode(state: State, prevState: State): ExitCode = {
    exitCodeFromStateNext(state) match {
      case ExitCode.Success => exitCodeFromStateOnFailure(state, prevState)
      case x                => x
    }
  }

  // State's "next" field indicates the next action for the command processor to take
  // we'll use that to determine if the command failed
  private[this] def exitCodeFromStateNext(state: State): ExitCode = {
    state.next match {
      case State.Continue       => ExitCode.Success
      case State.ClearGlobalLog => ExitCode.Success
      case State.KeepLastLog    => ExitCode.Success
      case ret: State.Return =>
        ret.result match {
          case exit: xsbti.Exit  => ExitCode(exit.code().toLong)
          case _: xsbti.Continue => ExitCode.Success
          case _: xsbti.Reboot   => ExitCode.Success
          case x =>
            val clazz = if (x eq null) "" else " (class: " + x.getClass + ")"
            state.log debug s"Unknown main result: $x$clazz"
            ExitCode.Unknown
        }
    }
  }

  // the shell command specifies an onFailure so that if an exception is thrown
  // it's handled by executing the shell again, instead of the state failing
  // so we also use that to indicate that the execution failed
  private[this] def exitCodeFromStateOnFailure(state: State, prevState: State): ExitCode =
    if (prevState.onFailure.isDefined && state.onFailure.isEmpty &&
        state.currentCommand.fold(true)(_ != StashOnFailure)) ExitCode(ErrorCodes.UnknownError)
    else ExitCode.Success

}
