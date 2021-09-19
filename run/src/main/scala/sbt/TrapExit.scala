/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.util.Logger

/**
 * Provides an approximation to isolated execution within a single JVM.
 * System.exit calls are trapped to prevent the JVM from terminating.  This is useful for executing
 * user code that may call System.exit, but actually exiting is undesirable.
 *
 * Exit is simulated by disposing all top-level windows and interrupting user-started threads.
 * Threads are not stopped and shutdown hooks are not called.  It is
 * therefore inappropriate to use this with code that requires shutdown hooks, creates threads that
 * do not terminate, or if concurrent AWT applications are run.
 * This category of code should only be called by forking a new JVM.
 */
object TrapExit {

  /**
   * Run `execute` in a managed context, using `log` for debugging messages.
   * `installManager` must be called before calling this method.
   */
  @deprecated("TrapExit feature is removed; just call the function instead", "1.6.0")
  def apply(execute: => Unit, log: Logger): Int =
    runUnmanaged(execute, log)

  /**
   * Installs the SecurityManager that implements the isolation and returns the previously installed SecurityManager, which may be null.
   * This method must be called before using `apply`.
   */
  @deprecated("TrapExit feature is removed; just call the function instead", "1.6.0")
  def installManager(): Nothing =
    sys.error("TrapExit feature is removed due to JDK 17 deprecating SecurityManager")

  /** Uninstalls the isolation SecurityManager and restores the old security manager. */
  @deprecated("TrapExit feature is removed; just call the function instead", "1.6.0")
  def uninstallManager(previous: Any): Unit = ()

  private[this] def runUnmanaged(execute: => Unit, log: Logger): Int = {
    log.warn("Managed execution not possible: security manager not installed.")
    try {
      execute; 0
    } catch {
      case e: Exception =>
        log.error("Error during execution: " + e.toString)
        log.trace(e)
        1
    }
  }
}
