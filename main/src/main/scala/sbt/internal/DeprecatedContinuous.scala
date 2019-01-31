/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import sbt.internal.io.{ WatchState => WS }

private[internal] trait DeprecatedContinuous {
  protected type Event = sbt.io.FileEventMonitor.Event[FileAttributes]
  protected type StartMessage = Option[Either[WS => String, Int => Option[String]]]
  protected type TriggerMessage = Option[Either[WS => String, (Int, Event) => Option[String]]]
  protected type DeprecatedWatchState = WS
  protected val deprecatedWatchingMessage = sbt.Keys.watchingMessage
  protected val deprecatedTriggeredMessage = sbt.Keys.triggeredMessage
}
