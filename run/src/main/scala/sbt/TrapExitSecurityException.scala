/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

/**
 * A custom SecurityException that tries not to be caught.  Closely based on a similar class in Nailgun.
 * The main goal of this exception is that once thrown, it propagates all of the way up the call stack,
 * terminating the thread's execution.
 */
private final class TrapExitSecurityException(val exitCode: Int) extends SecurityException {
  override def printStackTrace = throw this
  override def toString = throw this
  override def getCause = throw this
  override def getMessage = throw this
  override def fillInStackTrace = throw this
  override def getLocalizedMessage = throw this
}
