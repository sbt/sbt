/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.nio.file.Path

import sbt.internal.io.{ WatchState => WS }

private[internal] trait DeprecatedContinuous {
  protected type StartMessage =
    Option[Either[WS => String, (Int, String, Seq[String]) => Option[String]]]
  protected type TriggerMessage = Either[WS => String, (Int, Path, Seq[String]) => Option[String]]
  protected type DeprecatedWatchState = WS
  protected val deprecatedWatchingMessage = sbt.Keys.watchingMessage
  protected val deprecatedTriggeredMessage = sbt.Keys.triggeredMessage
}
