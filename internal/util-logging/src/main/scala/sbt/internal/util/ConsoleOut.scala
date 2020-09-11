/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import java.io.{ BufferedWriter, PrintStream, PrintWriter }
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

sealed trait ConsoleOut {
  val lockObject: AnyRef
  def print(s: String): Unit
  def println(s: String): Unit
  def println(): Unit
  def flush(): Unit
}

object ConsoleOut {
  def systemOut: ConsoleOut = terminalOut
  private[sbt] object NullConsoleOut extends ConsoleOut {
    override val lockObject: AnyRef = this
    override def print(s: String): Unit = {}
    override def println(): Unit = {}
    override def println(s: String): Unit = {}
    override def flush(): Unit = {}
  }
  private[sbt] def globalProxy: ConsoleOut = Proxy
  private[sbt] def setGlobalProxy(out: ConsoleOut): Unit = Proxy.set(out)
  private[sbt] def getGlobalProxy: ConsoleOut = Proxy.proxy.get
  private object Proxy extends ConsoleOut {
    private[ConsoleOut] val proxy = new AtomicReference[ConsoleOut](systemOut)
    private[this] def get: ConsoleOut = proxy.get
    def set(proxy: ConsoleOut): Unit = this.proxy.set(proxy)
    override val lockObject: AnyRef = proxy
    override def print(s: String): Unit = get.print(s)
    override def println(s: String): Unit = get.println(s)
    override def println(): Unit = get.println()
    override def flush(): Unit = get.flush()
    override def toString: String = s"ProxyConsoleOut"
  }

  def overwriteContaining(s: String): (String, String) => Boolean =
    (cur, prev) => cur.contains(s) && prev.contains(s)

  /** Move to beginning of previous line and clear the line. */
  private[this] final val OverwriteLine = "\u001B[A\r\u001B[2K"

  /**
   * ConsoleOut instance that is backed by System.out.  It overwrites the previously printed line
   * if the function `f(lineToWrite, previousLine)` returns true.
   *
   * The ConsoleOut returned by this method assumes that the only newlines are from println calls
   * and not in the String arguments.
   */
  def systemOutOverwrite(f: (String, String) => Boolean): ConsoleOut = new ConsoleOut {
    val lockObject = System.out
    private[this] var last: Option[String] = None
    private[this] var current = new java.lang.StringBuffer
    def print(s: String): Unit = synchronized { current.append(s); () }
    def println(s: String): Unit = synchronized { current.append(s); println() }
    def println(): Unit = synchronized {
      val s = current.toString
      if (ConsoleAppender.formatEnabledInEnv && last.exists(lmsg => f(s, lmsg)))
        lockObject.print(OverwriteLine)
      lockObject.println(s)
      last = Some(s)
      current.setLength(0)
    }
    def flush(): Unit = synchronized {
      val s = current.toString
      if (ConsoleAppender.formatEnabledInEnv && last.exists(lmsg => f(s, lmsg)))
        lockObject.print(OverwriteLine)
      lockObject.print(s)
      last = Some(s)
      current.setLength(0)
    }
    override def toString: String = s"SystemOutOverwrite@${System.identityHashCode(this)}"
  }

  def terminalOut: ConsoleOut = new ConsoleOut {
    override val lockObject: AnyRef = System.out
    override def print(s: String): Unit = Terminal.get.printStream.print(s)
    override def println(s: String): Unit = Terminal.get.printStream.println(s)
    override def println(): Unit = Terminal.get.printStream.println()
    override def flush(): Unit = Terminal.get.printStream.flush()
    override def toString: String = s"TerminalOut"
  }

  private[this] val consoleOutPerTerminal = new ConcurrentHashMap[Terminal, ConsoleOut]
  def terminalOut(terminal: Terminal): ConsoleOut = consoleOutPerTerminal.get(terminal) match {
    case null =>
      val res = new ConsoleOut {
        override val lockObject: AnyRef = terminal
        override def print(s: String): Unit = terminal.printStream.print(s)
        override def println(s: String): Unit = terminal.printStream.println(s)
        override def println(): Unit = terminal.printStream.println()
        override def flush(): Unit = terminal.printStream.flush()
        override def toString: String = s"TerminalOut($terminal)"
      }
      consoleOutPerTerminal.put(terminal, res)
      res
    case c => c
  }
  def printStreamOut(out: PrintStream): ConsoleOut = new ConsoleOut {
    val lockObject = out
    def print(s: String) = out.print(s)
    def println(s: String) = out.println(s)
    def println() = out.println()
    def flush() = out.flush()
    override def toString: String = s"PrintStreamConsoleOut($out)"
  }
  def printWriterOut(out: PrintWriter): ConsoleOut = new ConsoleOut {
    val lockObject = out
    def print(s: String) = out.print(s)
    def println(s: String) = { out.println(s); flush() }
    def println() = { out.println(); flush() }
    def flush() = { out.flush() }
    override def toString: String = s"PrintWriterConsoleOut($out)"
  }
  def bufferedWriterOut(out: BufferedWriter): ConsoleOut = new ConsoleOut {
    val lockObject = out
    def print(s: String) = out.write(s)
    def println(s: String) = { out.write(s); println() }
    def println() = { out.newLine(); flush() }
    def flush() = { out.flush() }
    override def toString: String = s"BufferedWriterConsoleOut($out)"
  }
}
