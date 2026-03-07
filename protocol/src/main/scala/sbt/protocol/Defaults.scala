/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.protocol

import scala.concurrent.duration.*

/**
 * Default duration constants for sbt server protocol operations.
 */
private[protocol] object Defaults {

  /** Default timeout for task execution (used by SbtRunnerConfig). */
  val ResponseTimeout: FiniteDuration = 1.minutes

  /** Timeout for waiting for the sbt portfile to be created. */
  val PortfileTimeout: FiniteDuration = 1.minute

  /** Timeout for the initialize handshake with the sbt server. */
  val InitializeTimeout: FiniteDuration = 10.seconds

  /** Time to wait for the sbt process to exit gracefully after sending shutdown. */
  val GracefulShutdownTimeout: FiniteDuration = 5.seconds

  /** Time to wait for the sbt process to exit after calling destroy(). */
  val DestroyTimeout: FiniteDuration = 10.seconds

  /** Time to wait for the read thread to finish when closing the session. */
  val ThreadDestroyTimeout: FiniteDuration = 5.seconds

  /** Interval between progress log messages while waiting for the portfile. */
  val PortfileLogInterval: FiniteDuration = 10.seconds
}
