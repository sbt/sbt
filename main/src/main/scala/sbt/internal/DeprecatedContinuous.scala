/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

import com.github.ghik.silencer.silent
import sbt.{ ProjectRef, State, Watched }
import sbt.internal.io.{ EventMonitor, Source, WatchState => WS }
import sbt.internal.util.AttributeKey
import sbt.nio.file.Glob

@silent
private[internal] trait DeprecatedContinuous {
  protected type StartMessage =
    Option[Either[WS => String, (Int, ProjectRef, Seq[String]) => Option[String]]]
  protected type TriggerMessage = Either[WS => String, (Int, Path, Seq[String]) => Option[String]]
  protected type DeprecatedWatchState = WS
  protected val deprecatedWatchingMessage = sbt.Keys.watchingMessage
  protected val deprecatedTriggeredMessage = sbt.Keys.triggeredMessage
  protected def watchState(globs: Seq[Glob], count: Int): WS = {
    WS.empty(globs).withCount(count)
  }
  private[this] val legacyWatchState =
    AttributeKey[AtomicReference[WS]]("legacy-watch-state", Int.MaxValue)
  private[sbt] def addLegacyWatchSetting(state: State): State = {
    val legacyState = new AtomicReference[WS](WS.empty(Nil).withCount(1))
    state
      .put(
        Watched.ContinuousEventMonitor,
        new EventMonitor {

          /** Block indefinitely until the monitor receives a file event or the user stops the watch. */
          override def awaitEvent(): Boolean = false

          /** A snapshot of the WatchState that includes the number of build triggers and watch sources. */
          override def state(): WS = legacyState.get()
          override def close(): Unit = ()
        }
      )
      .put(legacyWatchState, legacyState)
      .put(Watched.Configuration, new Watched {
        override def watchSources(s: State): Seq[Source] =
          s.get(legacyWatchState).map(_.get.sources).getOrElse(Nil)
      })
  }
  def updateLegacyWatchState(state: State, globs: Seq[Glob], count: Int): Unit = {
    state.get(legacyWatchState).foreach { ref =>
      val ws = WS.empty(globs).withCount(count)
      ref.set(ws)
    }
  }
}

@silent
private[sbt] object DeprecatedContinuous {
  private[sbt] val taskDefinitions: Seq[Def.Setting[_]] = Seq(
    sbt.Keys.watchTransitiveSources := sbt.Defaults.watchTransitiveSourcesTask.value,
    sbt.Keys.watch := sbt.Defaults.watchSetting.value,
    sbt.nio.Keys.watchTasks := Continuous.continuousTask.evaluated,
  )
}
