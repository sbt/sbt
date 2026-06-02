/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.BasicCommandStrings.{ StashOnFailure, networkExecPrefix }
import sbt.ProjectExtra.*
import sbt.internal.{ ConsoleChannel, FastTrackCommands, ShutdownHooks, SysProp, TaskProgress }
import sbt.internal.langserver.ErrorCodes
import sbt.internal.nio.CheckBuildSources.CheckBuildSourcesKey
import sbt.internal.protocol.JsonRpcResponseError
import sbt.internal.util.{
  AttributeKey,
  ErrorHandling,
  GlobalLogBacking,
  Prompt,
  Terminal as ITerminal
}
import sbt.io.{ IO, Using }
import sbt.io.syntax.*
import sbt.librarymanagement.SbtArtifacts
import sbt.protocol.*
import sbt.util.{ Logger, LoggerContext }

import java.io.PrintWriter
import java.util.Properties
import java.util.concurrent.RejectedExecutionException
import scala.annotation.tailrec
import scala.concurrent.duration.*
import scala.util.control.NonFatal
import sbt.internal.server.NetworkChannel

import java.text.ParseException

private[sbt] object MainLoop:

  /** Entry point to run the remaining commands in State with managed global logging. */
  def runLogged(state: State): xsbti.MainResult =
    // We've disabled jline shutdown hooks to prevent classloader leaks, and have been careful to always restore
    // the jline terminal in finally blocks, but hitting ctrl+c prevents finally blocks from being executed, in that
    // case the only way to restore the terminal is in a shutdown hook.
    val shutdownHook = ShutdownHooks.add(ITerminal.restore)
    try runLoggedLoop(state, state.globalLogging.backing)
    finally
      shutdownHook.close()
      ()

  /** Run loop that evaluates remaining commands and manages changes to global logging configuration. */
  @tailrec def runLoggedLoop(state: State, logBacking: GlobalLogBacking): xsbti.MainResult =
    runAndClearLast(state, logBacking) match
      // delete current and last log files when exiting normally
      case RunNext.Return(result) =>
        logBacking.file.delete()
        deleteLastLog(logBacking)
        result
      // delete previous log file, move current to previous, and start writing to a new file
      case RunNext.ClearGlobalLog(state) =>
        deleteLastLog(logBacking)
        runLoggedLoop(state, logBacking.shiftNew())
      // make previous log file the current log file
      case RunNext.KeepGlobalLog(state) =>
        logBacking.file.delete
        runLoggedLoop(state, logBacking.unshift)

  /** Runs the next sequence of commands, cleaning up global logging after any exceptions. */
  def runAndClearLast(state: State, logBacking: GlobalLogBacking): RunNext =
    try runWithNewLog(state, logBacking)
    catch
      case e: xsbti.FullReload =>
        deleteLastLog(logBacking)
        throw e // pass along a reboot request
      case e: RebootCurrent =>
        deleteLastLog(logBacking)
        deleteCurrentArtifacts(state)
        deleteZincBridgeSecondaryCache(
          state.log,
          BuildPaths.getZincDirectory(state, BuildPaths.getGlobalBase(state)),
        )
        throw new xsbti.FullReload(e.arguments.toArray, false)
      case NonFatal(e) =>
        System.err.println(
          "sbt appears to be exiting abnormally.\n  The log file for this session is at " + logBacking.file
        )
        deleteLastLog(logBacking)
        throw e

  /** Deletes the previous global log file. */
  def deleteLastLog(logBacking: GlobalLogBacking): Unit =
    logBacking.last.foreach(_.delete())

  /** Deletes the current sbt artifacts from boot. */
  private[sbt] def deleteCurrentArtifacts(state: State): Unit = {
    val provider = state.configuration.provider
    val appId = provider.id
    // If we can obtain boot directory more accurately it'd be better.
    val defaultBoot = BuildPaths.defaultGlobalBase / "boot"
    val buildProps = state.baseDir / "project" / "build.properties"
    // First try reading the sbt version from build.properties file.
    val sbtVersionOpt = if (buildProps.exists) {
      val buildProperties = new Properties()
      IO.load(buildProperties, buildProps)
      Option(buildProperties.getProperty("sbt.version")).map(_.trim)
    } else None
    val sbtVersion = sbtVersionOpt.getOrElse(appId.version)
    val currentArtDirs = defaultBoot * "*" / appId.groupID / appId.name / sbtVersion
    currentArtDirs.get().foreach { dir =>
      state.log.info(s"deleting $dir")
      IO.delete(dir)
    }
  }

  /** Removes the Zinc compiler bridge secondary cache (`zincDir/org.scala-sbt`). */
  private[sbt] def deleteZincBridgeSecondaryCache(log: Logger, zincDir: File): Unit =
    val bridgeCache = zincDir / SbtArtifacts.Organization
    if bridgeCache.exists() then
      log.info(s"deleting $bridgeCache")
      IO.delete(bridgeCache)

  /** Runs the next sequence of commands with global logging in place. */
  def runWithNewLog(state: State, logBacking: GlobalLogBacking): RunNext =
    Using.fileWriter(append = true)(logBacking.file) { writer =>
      val out = new PrintWriter(writer)
      val full = state.globalLogging.full
      val newLogging =
        state.globalLogging.newAppender(full, out, logBacking, LoggerContext.globalContext)
      // transferLevels(state, newLogging)
      val loggedState = state.copy(globalLogging = newLogging)
      try run(loggedState)
      finally
        out.close()
    }

  // /** Transfers logging and trace levels from the old global loggers to the new ones. */
  // private def transferLevels(state: State, logging: GlobalLogging): Unit = {
  //   val old = state.globalLogging
  //   Logger.transferLevels(old.backed, logging.backed)
  //   (old.full, logging.full) match { // well, this is a hack
  //     case (oldLog: AbstractLogger, newLog: AbstractLogger) => Logger.transferLevels(oldLog, newLog)
  //     case _                                                => ()
  //   }
  // }

  enum RunNext:
    case ClearGlobalLog(state: State)
    case KeepGlobalLog(state: State)
    case Return(result: xsbti.MainResult)

  def run(state: State): RunNext =
    val exchange = StandardMain.exchange
    runLoop(state)

  /** Runs the next sequence of commands that doesn't require global logging changes. */
  @tailrec def runLoop(state: State): RunNext =
    state.next match
      case State.Continue       => runLoop(next(state))
      case State.ClearGlobalLog => RunNext.ClearGlobalLog(state.continue)
      case State.KeepLastLog    => RunNext.KeepGlobalLog(state.continue)
      case ret: State.Return    => RunNext.Return(ret.result)

  def next(state: State): State =
    val context = LoggerContext()
    val superShellSleep =
      state.get(Keys.superShellSleep.key).getOrElse(SysProp.supershellSleep.millis)
    val superShellThreshold =
      state.get(Keys.superShellThreshold.key).getOrElse(SysProp.supershellThreshold)
    val resolutionProgress = new sbt.coursierint.ResolutionProgress
    val taskProgress =
      new TaskProgress(
        superShellSleep,
        superShellThreshold,
        state.log,
        resolutionProgress,
        Project.configNameToIdent(state)
      )
    val gcMonitor = if (SysProp.gcMonitor) Some(new sbt.internal.GCMonitor(state.log)) else None
    try {
      ErrorHandling.wideConvert {
        state
          .put(Keys.loggerContext, context)
          .put(Keys.taskProgress, taskProgress)
          .put(Keys.resolutionProgress, resolutionProgress)
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
      gcMonitor.foreach(_.close())
      context.close()
      taskProgress.close()
    }
  end next

  /** This is the main function State transfer function of the sbt command processing. */
  def processCommand(exec: Exec, state: State): State =
    val channelName = exec.source.map(_.channelName)
    val exchange = StandardMain.exchange
    exchange.setState(state)
    exchange.notifyStatus(
      ExecStatusEvent("Processing", channelName, exec.execId, Vector())
    )
    try {
      def process(): State = {
        def getOrSet[T](state: State, key: AttributeKey[T], value: Extracted => T): State = {
          state.get(key) match {
            case Some(_) => state
            case _       =>
              if (state.get(Keys.stateBuildStructure).isDefined) {
                val extracted = Project.extract(state)
                state.put(key, value(extracted))
              } else state
          }
        }

        val cmdProgressState =
          getOrSet(
            state,
            sbt.Keys.currentCommandProgress,
            extracted => EvaluateTask.executeProgress(extracted, extracted.structure, state)
          )

        exchange.setState(cmdProgressState)
        exchange.setExec(Some(exec))
        val (flushTerminal, termState) = channelName.flatMap(exchange.channelForName) match
          case Some(c) =>
            val prevTerminal = ITerminal.set(c.terminal)
            // temporarily set the prompt to running during task evaluation
            c.terminal.setPrompt(Prompt.Running)
            (() => c.terminal.flush()) -> cmdProgressState.put(
              Keys.terminalKey,
              Terminal(c.terminal)
            )
          case _ => (() => ()) -> cmdProgressState.put(Keys.terminalKey, Terminal(ITerminal.get))

        val currentCmdProgress =
          cmdProgressState.get(sbt.Keys.currentCommandProgress)
        currentCmdProgress.foreach(_.beforeCommand(exec.commandLine, cmdProgressState))
        /*
         * FastTrackCommands.evaluate can be significantly faster than Command.process because
         * it avoids an expensive parsing step for internal commands that are easy to parse.
         * Dropping (FastTrackCommands.evaluate ... getOrElse) should be functionally identical
         * but slower.
         */
        val prevSessionCurrent = termState.get(Keys.sessionSettings).map(_.current)
        val newState =
          try {
            var errorMsg: Option[String] = None
            val res = FastTrackCommands
              .evaluate(termState, exec.commandLine)
              .getOrElse(Command.process(exec.commandLine, termState, m => errorMsg = Some(m)))
            errorMsg match {
              case Some(msg) =>
                currentCmdProgress.foreach(
                  _.afterCommand(exec.commandLine, Left(new ParseException(msg, 0)))
                )
              case None => currentCmdProgress.foreach(_.afterCommand(exec.commandLine, Right(res)))
            }
            syncChannelCursor(res, prevSessionCurrent, channelName)
          } catch {
            case _: RejectedExecutionException =>
              val cancelled = new Cancelled(exec.commandLine)
              currentCmdProgress
                .foreach(_.afterCommand(exec.commandLine, Left(cancelled)))
              throw cancelled

            case e: Throwable =>
              currentCmdProgress
                .foreach(_.afterCommand(exec.commandLine, Left(e)))
              throw e
          } finally
            // Flush the terminal output after command evaluation to ensure that all output
            // is displayed in the thin client before we report the command status.
            flushTerminal()
        if (
          exec.execId.fold(true)(!_.startsWith(networkExecPrefix)) &&
          !exec.commandLine.startsWith(networkExecPrefix)
        ) {
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
        newState.get(sbt.Keys.currentCommandProgress).foreach(_.stop())
        newState
          .remove(Keys.terminalKey)
          .remove(Keys.currentCommandProgress)
      }

      val channel = channelName.flatMap(exchange.channelForName)
      val (canReload, useLoadp) = channel match
        case Some(nc: NetworkChannel) => (exec.execId.nonEmpty, true)
        case Some(_: ConsoleChannel)  => (true, false)
        case _                        => (false, false)

      state.get(CheckBuildSourcesKey) match {
        case Some(cbs) if canReload && cbs.needsReload(state, exec) =>
          val loadExec =
            if (useLoadp) Exec("loadp", exec.execId, exec.source)
            else Exec("reload", exec.source)

          loadExec +: exec +: state.remove(CheckBuildSourcesKey)
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
  end processCommand

  def logFullException(e: Throwable, log: Logger): Unit = State.logFullException(e, log)

  opaque type ExitCode = Option[Long]
  object ExitCode:
    def apply(n: Long): ExitCode = Option(n)
    val Success: ExitCode = ExitCode(0)
    val Unknown: ExitCode = None
  end ExitCode

  private def exitCode(state: State, prevState: State): ExitCode =
    exitCodeFromStateNext(state) match
      case ExitCode.Success => exitCodeFromStateOnFailure(state, prevState)
      case x                => x

  // State's "next" field indicates the next action for the command processor to take
  // we'll use that to determine if the command failed
  private def exitCodeFromStateNext(state: State): ExitCode =
    state.next match
      case State.Continue       => ExitCode.Success
      case State.ClearGlobalLog => ExitCode.Success
      case State.KeepLastLog    => ExitCode.Success
      case ret: State.Return    =>
        ret.result match
          case exit: xsbti.Exit  => ExitCode(exit.code().toLong)
          case _: xsbti.Continue => ExitCode.Success
          case _: xsbti.Reboot   => ExitCode.Success
          case x                 =>
            val clazz = if (x eq null) "" else " (class: " + x.getClass + ")"
            state.log.debug(s"Unknown main result: $x$clazz")
            ExitCode.Unknown

  // the shell command specifies an onFailure so that if an exception is thrown
  // it's handled by executing the shell again, instead of the state failing
  // so we also use that to indicate that the execution failed
  private def exitCodeFromStateOnFailure(state: State, prevState: State): ExitCode =
    if prevState.onFailure.isDefined && state.onFailure.isEmpty &&
      state.currentCommand.fold(true)(_.commandLine != StashOnFailure)
    then ExitCode(ErrorCodes.UnknownError)
    else ExitCode.Success

  private def syncChannelCursor(
      state: State,
      prevSessionCurrent: Option[ProjectRef],
      channelName: Option[String]
  ): State =
    channelName match
      case Some(cn) =>
        val newSessionCurrent = state.get(Keys.sessionSettings).map(_.current)
        (prevSessionCurrent, newSessionCurrent) match
          case (prev, Some(curr)) if prev != Some(curr) =>
            val cursors = state.get(Keys.channelProjectCursors).getOrElse(Map.empty)
            state.put(Keys.channelProjectCursors, cursors.updated(cn, curr))
          case _ => state
      case None => state

end MainLoop

// No stack trace since this is just to notify the user which command they cancelled
class Cancelled(cmdLine: String) extends Throwable(cmdLine, null, true, false) {
  override def toString: String = s"Cancelled: $cmdLine"
}
