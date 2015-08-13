/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mikko Peltonen, Stuart Roebuck, Mark Harrah
 */
package sbt

import BasicCommandStrings.ClearOnFailure
import State.FailureWall
import annotation.tailrec
import java.io.File
import Types.const

trait Watched {
  /** The files watched when an action is run with a preceeding ~ */
  def watchPaths(s: State): Seq[File] = Nil
  def terminateWatch(key: Int): Boolean = Watched.isEnter(key)
  /**
   * The time in milliseconds between checking for changes.  The actual time between the last change made to a file and the
   * execution time is between `pollInterval` and `pollInterval*2`.
   */
  def pollInterval: Int = Watched.PollDelayMillis
  /** The message to show when triggered execution waits for sources to change.*/
  def watchingMessage(s: WatchState): String = Watched.defaultWatchingMessage(s)
  /** The message to show before an action is run. */
  def triggeredMessage(s: WatchState): String = Watched.defaultTriggeredMessage(s)
}

object Watched {
  val defaultWatchingMessage: WatchState => String = _.count + ". Waiting for source changes... (press enter to interrupt)"
  val defaultTriggeredMessage: WatchState => String = const("")
  val clearWhenTriggered: WatchState => String = const(clearScreen)
  def clearScreen: String = "\033[2J\033[0;0H"

  private[this] class AWatched extends Watched

  def multi(base: Watched, paths: Seq[Watched]): Watched =
    new AWatched {
      override def watchPaths(s: State) = (base.watchPaths(s) /: paths)(_ ++ _.watchPaths(s))
      override def terminateWatch(key: Int): Boolean = base.terminateWatch(key)
      override val pollInterval = (base +: paths).map(_.pollInterval).min
      override def watchingMessage(s: WatchState) = base.watchingMessage(s)
      override def triggeredMessage(s: WatchState) = base.triggeredMessage(s)
    }
  def empty: Watched = new AWatched

  val PollDelayMillis = 500
  def isEnter(key: Int): Boolean = key == 10 || key == 13
  def printIfDefined(msg: String) = if (!msg.isEmpty) System.out.println(msg)

  def executeContinuously(watched: Watched, s: State, next: String, repeat: String): State =
    {
      @tailrec def shouldTerminate: Boolean = (System.in.available > 0) && (watched.terminateWatch(System.in.read()) || shouldTerminate)
      val sourcesFinder = PathFinder { watched watchPaths s }
      val watchState = s get ContinuousState getOrElse WatchState.empty

      if (watchState.count > 0)
        printIfDefined(watched watchingMessage watchState)

      val (triggered, newWatchState, newState) =
        try {
          val (triggered, newWatchState) = SourceModificationWatch.watch(sourcesFinder, watched.pollInterval, watchState)(shouldTerminate)
          (triggered, newWatchState, s)
        } catch {
          case e: Exception =>
            val log = s.log
            log.error("Error occurred obtaining files to watch.  Terminating continuous execution...")
            State.handleException(e, s, log)
            (false, watchState, s.fail)
        }

      if (triggered) {
        printIfDefined(watched triggeredMessage newWatchState)
        (ClearOnFailure :: next :: FailureWall :: repeat :: s).put(ContinuousState, newWatchState)
      } else {
        while (System.in.available() > 0) System.in.read()
        s.put(ContinuousState, WatchState.empty)
      }
    }
  val ContinuousState = AttributeKey[WatchState]("watch state", "Internal: tracks state for continuous execution.")
  val Configuration = AttributeKey[Watched]("watched-configuration", "Configures continuous execution.")
}