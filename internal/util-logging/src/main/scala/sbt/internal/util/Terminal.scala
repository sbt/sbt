/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import java.io.{ InputStream, OutputStream }
import java.nio.channels.ClosedChannelException
import java.util.Locale
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }
import java.util.concurrent.locks.ReentrantLock

import jline.console.ConsoleReader

import scala.util.control.NonFatal

object Terminal {

  /**
   * Gets the current width of the terminal. The implementation reads a property from the jline
   * config which is updated if it has been more than a second since the last update. It is thus
   * possible for this value to be stale.
   *
   * @return the terminal width.
   */
  def getWidth: Int = terminal.getWidth

  /**
   * Gets the current height of the terminal. The implementation reads a property from the jline
   * config which is updated if it has been more than a second since the last update. It is thus
   * possible for this value to be stale.
   *
   * @return the terminal height.
   */
  def getHeight: Int = terminal.getHeight

  /**
   * Returns true if the current terminal supports ansi characters.
   *
   * @return true if the current terminal supports ansi escape codes.
   */
  def isAnsiSupported: Boolean =
    try terminal.isAnsiSupported
    catch { case NonFatal(_) => !isWindows }

  /**
   * Returns true if System.in is attached. When sbt is run as a subprocess, like in scripted or
   * as a server, System.in will not be attached and this method will return false. Otherwise
   * it will return true.
   *
   * @return true if System.in is attached.
   */
  def systemInIsAttached: Boolean = attached.get

  /**
   * Returns an InputStream that will throw a [[ClosedChannelException]] if read returns -1.
   * @return the wrapped InputStream.
   */
  private[sbt] def throwOnClosedSystemIn: InputStream = new InputStream {
    override def available(): Int = WrappedSystemIn.available()
    override def read(): Int = WrappedSystemIn.read() match {
      case -1 => throw new ClosedChannelException
      case r  => r
    }
  }

  /**
   * Provides a wrapper around System.in. The wrapped stream in will check if the terminal is attached
   * in available and read. If a read returns -1, it will mark System.in as unattached so that
   * it can be detected by [[systemInIsAttached]].
   *
   * @return the wrapped InputStream
   */
  private[sbt] def wrappedSystemIn: InputStream = WrappedSystemIn

  /**
   * Restore the terminal to its initial state.
   */
  private[sbt] def restore(): Unit = terminal.restore()

  /**
   * Runs a thunk ensuring that the terminal has echo enabled. Most of the time sbt should have
   * echo mode on except when it is explicitly set to raw mode via [[withRawSystemIn]].
   *
   * @param f the thunk to run
   * @tparam T the result type of the thunk
   * @return the result of the thunk
   */
  private[sbt] def withEcho[T](toggle: Boolean)(f: => T): T = {
    val previous = terminal.isEchoEnabled
    terminalLock.lockInterruptibly()
    try {
      terminal.setEchoEnabled(toggle)
      f
    } finally {
      terminal.setEchoEnabled(previous)
      terminalLock.unlock()
    }
  }

  /**
   * Runs a thunk ensuring that the terminal is in canonical mode:
   * [[https://www.gnu.org/software/libc/manual/html_node/Canonical-or-Not.html Canonical or Not]].
   * Most of the time sbt should be in canonical mode except when it is explicitly set to raw mode
   * via [[withRawSystemIn]].
   *
   * @param f the thunk to run
   * @tparam T the result type of the thunk
   * @return the result of the thunk
   */
  private[sbt] def withCanonicalIn[T](f: => T): T = withTerminal { t =>
    t.restore()
    f
  }

  /**
   * Runs a thunk ensuring that the terminal is in in non-canonical mode:
   * [[https://www.gnu.org/software/libc/manual/html_node/Canonical-or-Not.html Canonical or Not]].
   * This should be used when sbt is reading user input, e.g. in `shell` or a continuous build.
   * @param f the thunk to run
   * @tparam T the result type of the thunk
   * @return the result of the thunk
   */
  private[sbt] def withRawSystemIn[T](f: => T): T = withTerminal { t =>
    t.init()
    f
  }

  private[this] def withTerminal[T](f: jline.Terminal => T): T = {
    val t = terminal
    terminalLock.lockInterruptibly()
    try f(t)
    finally {
      t.restore()
      terminalLock.unlock()
    }
  }

  private[this] object WrappedSystemIn extends InputStream {
    private[this] val in = terminal.wrapInIfNeeded(System.in)
    override def available(): Int = if (attached.get) in.available else 0
    override def read(): Int = synchronized {
      if (attached.get) {
        val res = in.read
        if (res == -1) attached.set(false)
        res
      } else -1
    }
  }

  private[this] val terminalLock = new ReentrantLock()
  private[this] val attached = new AtomicBoolean(true)
  private[this] val terminalHolder = new AtomicReference(wrap(jline.TerminalFactory.get))
  private[this] lazy val isWindows =
    System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).indexOf("windows") >= 0

  private[this] def wrap(terminal: jline.Terminal): jline.Terminal = {
    val term: jline.Terminal = new jline.Terminal {
      private[this] val hasConsole = System.console != null
      private[this] def alive = hasConsole && attached.get
      override def init(): Unit = if (alive) terminal.init()
      override def restore(): Unit = if (alive) terminal.restore()
      override def reset(): Unit = if (alive) terminal.reset()
      override def isSupported: Boolean = terminal.isSupported
      override def getWidth: Int = terminal.getWidth
      override def getHeight: Int = terminal.getHeight
      override def isAnsiSupported: Boolean = terminal.isAnsiSupported
      override def wrapOutIfNeeded(out: OutputStream): OutputStream = terminal.wrapOutIfNeeded(out)
      override def wrapInIfNeeded(in: InputStream): InputStream = terminal.wrapInIfNeeded(in)
      override def hasWeirdWrap: Boolean = terminal.hasWeirdWrap
      override def isEchoEnabled: Boolean = terminal.isEchoEnabled
      override def setEchoEnabled(enabled: Boolean): Unit = if (alive) {
        terminal.setEchoEnabled(enabled)
      }
      override def disableInterruptCharacter(): Unit =
        if (alive) terminal.disableInterruptCharacter()
      override def enableInterruptCharacter(): Unit =
        if (alive) terminal.enableInterruptCharacter()
      override def getOutputEncoding: String = terminal.getOutputEncoding
    }
    term.restore()
    term.setEchoEnabled(true)
    term
  }

  private[util] def reset(): Unit = {
    jline.TerminalFactory.reset()
    terminalHolder.set(wrap(jline.TerminalFactory.get))
  }

  // translate explicit class names to type in order to support
  //  older Scala, since it shaded classes but not the system property
  private[this] def fixTerminalProperty(): Unit = {
    val terminalProperty = "jline.terminal"
    val newValue = System.getProperty(terminalProperty) match {
      case "jline.UnixTerminal"                             => "unix"
      case null if System.getProperty("sbt.cygwin") != null => "unix"
      case "jline.WindowsTerminal"                          => "windows"
      case "jline.AnsiWindowsTerminal"                      => "windows"
      case "jline.UnsupportedTerminal"                      => "none"
      case x                                                => x
    }
    if (newValue != null) {
      System.setProperty(terminalProperty, newValue)
      ()
    }
  }
  fixTerminalProperty()

  private[sbt] def createReader(in: InputStream): ConsoleReader =
    new ConsoleReader(in, System.out, terminal)

  private[this] def terminal: jline.Terminal = terminalHolder.get match {
    case null => throw new IllegalStateException("Uninitialized terminal.")
    case term => term
  }

  @deprecated("For compatibility only", "1.4.0")
  private[sbt] def deprecatedTeminal: jline.Terminal = terminal
}
