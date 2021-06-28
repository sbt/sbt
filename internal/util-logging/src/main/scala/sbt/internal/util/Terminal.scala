/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import java.io.{ InputStream, InterruptedIOException, IOException, OutputStream, PrintStream }
import java.nio.channels.ClosedChannelException
import java.util.{ Arrays, Locale }
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }
import java.util.concurrent.{ Executors, LinkedBlockingQueue, TimeUnit }

import jline.console.ConsoleReader
import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

trait Terminal extends AutoCloseable {

  /**
   * Gets the current width of the terminal. The implementation reads a property from the jline
   * config which is updated if it has been more than a second since the last update. It is thus
   * possible for this value to be stale.
   *
   * @return the terminal width.
   */
  def getWidth: Int

  /**
   * Gets the current height of the terminal. The implementation reads a property from the jline
   * config which is updated if it has been more than a second since the last update. It is thus
   * possible for this value to be stale.
   *
   * @return the terminal height.
   */
  def getHeight: Int

  /**
   * Returns the height and width of the current line that is displayed on the terminal. If the
   * most recently flushed byte is a newline, this will be `(0, 0)`.
   *
   * @return the (height, width) pair
   */
  def getLineHeightAndWidth(line: String): (Int, Int)

  /**
   * Gets the input stream for this Terminal. This could be a wrapper around System.in for the
   * process or it could be a remote input stream for a network channel.
   * @return the input stream.
   */
  def inputStream: InputStream

  /**
   * Gets the output stream for this Terminal.
   * @return the output stream.
   */
  def outputStream: OutputStream

  /**
   * Gets the error stream for this Terminal.
   * @return the error stream.
   */
  def errorStream: OutputStream

  /**
   * Returns true if the terminal supports ansi characters.
   *
   * @return true if the terminal supports ansi escape codes.
   */
  def isAnsiSupported: Boolean

  /**
   * Returns true if color is enabled for this terminal.
   *
   * @return true if color is enabled for this terminal.
   */
  def isColorEnabled: Boolean

  /**
   * Returns true if the terminal has echo enabled.
   *
   * @return true if the terminal has echo enabled.
   */
  def isEchoEnabled: Boolean

  /**
   * Returns true if the terminal has success enabled, which it may not if it is for batch
   * commands because the client will print the success results when received from the
   * server.
   *
   * @return true if the terminal has success enabled
   */
  def isSuccessEnabled: Boolean

  /**
   * Returns true if the terminal has supershell enabled.
   *
   * @return true if the terminal has supershell enabled.
   */
  def isSupershellEnabled: Boolean

  /**
   * Toggles whether or not the terminal should echo characters back to stdout
   *
   * @return the previous value of the toggle
   */
  def setEchoEnabled(toggle: Boolean): Unit

  /*
   * The methods below this comment are implementation details that are in
   * some cases specific to jline2. These methods may need to change or be
   * removed if/when sbt upgrades to jline 3.
   */

  /**
   * Returns the last line written to the terminal's output stream.
   * @return the last line
   */
  private[sbt] def getLastLine: Option[String]

  /**
   * Returns the buffered lines that have been written to the terminal. The
   * main use case is to display the system startup log lines when a client
   * connects to a booting server. This could also be used to implement a more
   * tmux like experience where multiple clients connect to the same console.
   *
   * @return the lines
   */
  private[sbt] def getLines: Seq[String]

  private[sbt] def getBooleanCapability(capability: String): Boolean
  private[sbt] def getNumericCapability(capability: String): Integer
  private[sbt] def getStringCapability(capability: String): String
  private[sbt] def getAttributes: Map[String, String]
  private[sbt] def setAttributes(attributes: Map[String, String]): Unit
  private[sbt] def setSize(width: Int, height: Int): Unit

  private[sbt] def name: String
  private[sbt] final def withRawInput[T](f: => T): T = {
    enterRawMode()
    try f
    catch { case e: InterruptedIOException => throw new InterruptedException } finally exitRawMode()
  }
  private[sbt] def enterRawMode(): Unit
  private[sbt] def exitRawMode(): Unit
  private[sbt] def write(bytes: Int*): Unit
  private[sbt] def printStream: PrintStream
  private[sbt] def withPrintStream[T](f: PrintStream => T): T
  private[sbt] def withRawOutput[R](f: => R): R
  private[sbt] def restore(): Unit = {}
  private[sbt] def progressState: ProgressState
  private[this] val promptHolder: AtomicReference[Prompt] = new AtomicReference(Prompt.Batch)
  private[sbt] final def prompt: Prompt = promptHolder.get
  private[sbt] final def setPrompt(newPrompt: Prompt): Unit =
    if (prompt != Prompt.NoPrompt) promptHolder.set(newPrompt)

  /**
   * Returns the number of lines that the input string will cover given the current width of the
   * terminal.
   *
   * @param line the input line
   * @return the number of lines that the line will cover on the terminal
   */
  private[sbt] def lineCount(line: String): Int = {
    val lines = EscHelpers.stripColorsAndMoves(line).split('\n')
    val width = getWidth
    def count(l: String): Int = {
      val len = l.length
      if (width > 0 && len > 0) (len - 1 + width) / width else 0
    }
    if (lines.nonEmpty) lines.tail.foldLeft(lines.headOption.fold(0)(count))(_ + count(_))
    else 0
  }
  private[sbt] def flush(): Unit = printStream.flush()
}

object Terminal {
  val NO_BOOT_CLIENTS_CONNECTED: Int = -2
  // Disable noisy jline log spam
  if (System.getProperty("sbt.jline.verbose", "false") != "true")
    jline.internal.Log.setOutput(new PrintStream(_ => {}, false))
  private[this] val isCI = System.getProperty("sbt.ci", "") == "true" ||
    sys.env.contains("BUILD_NUMBER") || sys.env.contains("CI")
  def consoleLog(string: String): Unit = {
    try Terminal.console.printStream.println(s"[info] $string")
    catch { case _: IOException => }
  }
  private[sbt] def set(terminal: Terminal): Terminal = activeTerminal.getAndSet(terminal)
  implicit class TerminalOps(private val term: Terminal) extends AnyVal {
    def ansi(richString: => String, string: => String): String =
      if (term.isAnsiSupported) richString else string
    /*
     * Whenever we are dealing with JLine, which is true in sbt's ConsoleReader
     * as well as in the scala `console` task, we need to provide a jline.Terminal2
     * instance that can be consumed by the ConsoleReader. The ConsoleTerminal
     * already wraps a jline terminal, so we can just return the wrapped jline
     * terminal.
     */
    private[sbt] def toJLine: jline.Terminal with jline.Terminal2 = term match {
      case _ =>
        new jline.Terminal with jline.Terminal2 {
          override def init(): Unit = {}
          override def restore(): Unit = {}
          override def reset(): Unit = {}
          override def isSupported: Boolean = true
          override def getWidth: Int = term.getWidth
          override def getHeight: Int = term.getHeight
          override def isAnsiSupported: Boolean = term.isAnsiSupported
          override def wrapOutIfNeeded(out: OutputStream): OutputStream = out
          override def wrapInIfNeeded(in: InputStream): InputStream = in
          override def hasWeirdWrap: Boolean = false
          override def isEchoEnabled: Boolean = term.isEchoEnabled
          override def setEchoEnabled(enabled: Boolean): Unit = {}
          override def disableInterruptCharacter(): Unit = {}
          override def enableInterruptCharacter(): Unit = {}
          override def getOutputEncoding: String = null
          override def getBooleanCapability(capability: String): Boolean =
            term.getBooleanCapability(capability)
          override def getNumericCapability(capability: String): Integer =
            term.getNumericCapability(capability)
          override def getStringCapability(capability: String): String =
            term.getStringCapability(capability)
        }
    }
  }

  /*
   * Closes the standard input and output streams for the process. This allows
   * the sbt client to detach from the server it launches.
   */
  def close(): Unit = {
    if (System.console == null) {
      originalOut.close()
      originalIn.close()
      originalErr.close()
    }
  }

  /**
   * Returns true if System.in is attached. When sbt is run as a subprocess, like in scripted or
   * as a server, System.in will not be attached and this method will return false. Otherwise
   * it will return true.
   *
   * @return true if System.in is attached.
   */
  def systemInIsAttached: Boolean = attached.get

  def read: Int = inputStream.get match {
    case null => -1
    case is   => is.read
  }

  /**
   * Returns an InputStream that will throw a [[ClosedChannelException]] if read returns -1.
   * @return the wrapped InputStream.
   */
  private[sbt] def throwOnClosedSystemIn(in: InputStream): InputStream = new InputStream {
    override def available(): Int = in.available()
    override def read(): Int = in.read() match {
      case -1          => throw new ClosedChannelException
      case r if r >= 0 => r
      case _           => -1
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
  private[sbt] def restore(): Unit = console.toJLine.restore()

  private[this] val hasProgress: AtomicBoolean = new AtomicBoolean(false)

  private[sbt] def parseLogOption(s: String): Option[Boolean] =
    s.toLowerCase match {
      case "always" => Some(true)
      case "auto"   => None
      case "never"  => Some(false)
      case "true"   => Some(true)
      case "false"  => Some(false)
      case _        => None
    }

  /**
   * Indicates whether formatting has been disabled in environment variables.
   * 1. -Dsbt.log.noformat=true means no formatting.
   * 2. -Dsbt.color=always/auto/never/true/false
   * 3. -Dsbt.colour=always/auto/never/true/false
   * 4. -Dsbt.log.format=always/auto/never/true/false
   */
  private[this] lazy val logFormatEnabled: Option[Boolean] = {
    sys.props.get("sbt.log.noformat") match {
      case Some(_) => Some(!java.lang.Boolean.getBoolean("sbt.log.noformat"))
      case _       => sys.props.get("sbt.log.format").flatMap(parseLogOption)
    }
  }
  private[sbt] lazy val isAnsiSupported: Boolean = logFormatEnabled.getOrElse(useColorDefault)

  private[this] val isDumb = "dumb" == System.getenv("TERM")
  private[this] def isDumbTerminal = isDumb || System.getProperty("jline.terminal", "") == "none"
  private[this] val hasConsole = Option(java.lang.System.console).isDefined
  private[this] def useColorDefault: Boolean = {
    // This approximates that both stdin and stdio are connected,
    // so by default color will be turned off for pipes and redirects.
    props
      .map(_.color)
      .orElse(isColorEnabledProp)
      .getOrElse(
        logFormatEnabled
          .getOrElse(true) && ((hasConsole && !isDumbTerminal) || isCI || Util.isEmacs)
      )
  }
  private[this] lazy val isColorEnabledProp: Option[Boolean] =
    sys.props.get("sbt.color").orElse(sys.props.get("sbt.colour")).flatMap(parseLogOption)
  private[sbt] lazy val isColorEnabled = useColorDefault

  private[sbt] def red(str: String, doRed: Boolean): String =
    if (isColorEnabled && doRed) Console.RED + str + Console.RESET
    else str

  private[this] def hasVirtualIO = System.getProperty("sbt.io.virtual", "") == "true" || !isCI
  private[sbt] def canPollSystemIn: Boolean = hasConsole && !isDumbTerminal && hasVirtualIO

  /**
   *
   * @param isServer toggles whether or not this is a server of client process
   * @param f the thunk to run
   * @tparam T the result type of the thunk
   * @return the result of the thunk
   */
  private[sbt] def withStreams[T](isServer: Boolean, isSubProcess: Boolean)(f: => T): T = {
    // In ci environments, don't touch the io streams unless run with -Dsbt.io.virtual=true
    if ((hasConsole && !isDumbTerminal) || isSubProcess)
      consoleTerminalHolder.set(newConsoleTerminal())
    if (hasVirtualIO) {
      hasProgress.set(isServer && isAnsiSupported)
      Terminal.set(consoleTerminalHolder.get)
      try withOut(withIn(f))
      finally {
        jline.TerminalFactory.reset()
        if (isServer) {
          console match {
            case c: ConsoleTerminal if !isWindows =>
              /*
               * Entering raw mode in this way causes the standard in InputStream
               * to become non-blocking. After we set it to non-blocking, we spin
               * up a thread that reads from the inputstream and the resets it
               * back to blocking mode. We can then close the console. We do
               * this on a background thread in case the read blocks indefinitely.
               */
              c.system.enterRawMode()
              val runnable: Runnable = () => {
                try Util.ignoreResult(c.inputStream.read)
                catch { case _: InterruptedException => }
              }
              val thread = new Thread(runnable, "sbt-console-background-close")
              thread.setDaemon(true)
              thread.start()
              // The thread should exit almost instantly but give it 200ms to spin up
              thread.join(200)
              if (thread.isAlive) thread.interrupt()
              c.close()
            case c => c.close()
          }
        } else {
          console.close()
        }
      }
    } else f
  }

  private[this] object ProxyTerminal extends Terminal {
    private def t: Terminal = {
      val current = activeTerminal.get
      // if the activeTerminal is yet to be initialized on use,
      // initialize to the conventional simple terminal for compatibility and testing
      if (current ne null) current
      else {
        Terminal.set(Terminal.SimpleTerminal)
        activeTerminal.get
      }
    }
    override private[sbt] def progressState: ProgressState = t.progressState
    override private[sbt] def enterRawMode(): Unit = t.enterRawMode()
    override private[sbt] def exitRawMode(): Unit = t.exitRawMode()
    override def getWidth: Int = t.getWidth
    override def getHeight: Int = t.getHeight
    override def getLineHeightAndWidth(line: String): (Int, Int) = t.getLineHeightAndWidth(line)
    override def lineCount(line: String): Int = t.lineCount(line)
    override def inputStream: InputStream = t.inputStream
    override def outputStream: OutputStream = t.outputStream
    override def errorStream: OutputStream = t.errorStream
    override def isAnsiSupported: Boolean = t.isAnsiSupported
    override def isColorEnabled: Boolean = t.isColorEnabled
    override def isEchoEnabled: Boolean = t.isEchoEnabled
    override def isSuccessEnabled: Boolean = t.isSuccessEnabled
    override def isSupershellEnabled: Boolean = t.isSupershellEnabled
    override def setEchoEnabled(toggle: Boolean): Unit = t.setEchoEnabled(toggle)
    override def getBooleanCapability(capability: String): Boolean =
      t.getBooleanCapability(capability)
    override def getNumericCapability(capability: String): Integer =
      t.getNumericCapability(capability)
    override def getStringCapability(capability: String): String =
      t.getStringCapability(capability)
    override private[sbt] def getAttributes: Map[String, String] = t.getAttributes
    override private[sbt] def setAttributes(attributes: Map[String, String]): Unit =
      t.setAttributes(attributes)
    override private[sbt] def setSize(width: Int, height: Int): Unit = t.setSize(width, height)
    override def printStream: PrintStream = t.printStream
    override def withPrintStream[T](f: PrintStream => T): T = t.withPrintStream(f)
    override private[sbt] def withRawOutput[R](f: => R): R = t.withRawOutput(f)
    override def restore(): Unit = t.restore()
    override def close(): Unit = {}
    override private[sbt] def write(bytes: Int*): Unit = t.write(bytes: _*)
    override def getLastLine: Option[String] = t.getLastLine
    override def getLines: Seq[String] = t.getLines
    override private[sbt] def name: String = t.name
    override def toString: String = s"ProxyTerminal(current = $t)"
  }
  private[sbt] def get: Terminal = ProxyTerminal

  private[sbt] def withIn[T](in: InputStream)(f: => T): T = {
    val original = inputStream.get
    try {
      inputStream.set(in)
      System.setIn(in)
      scala.Console.withIn(in)(f)
    } finally {
      inputStream.set(original)
      System.setIn(original)
    }
  }

  private[sbt] def withOut[T](out: PrintStream)(f: => T): T = {
    val originalOut = System.out
    val originalErr = System.err
    val originalProxyOut = ConsoleOut.getGlobalProxy
    try {
      ConsoleOut.setGlobalProxy(ConsoleOut.printStreamOut(out))
      System.setOut(out)
      System.setErr(out)
      scala.Console.withErr(out)(scala.Console.withOut(out)(f))
    } finally {
      ConsoleOut.setGlobalProxy(originalProxyOut)
      System.setOut(originalOut)
      System.setErr(originalErr)
    }
  }

  val sepBytes = System.lineSeparator.getBytes("UTF-8")
  private class LinePrintStream(outputStream: OutputStream)
      extends PrintStream(outputStream, true) {
    override def println(s: String): Unit = synchronized {
      out.write(s.getBytes("UTF-8") ++ sepBytes)
      out.flush()
    }
  }
  private[this] val originalOut = new LinePrintStream(System.out)
  private[this] val originalErr = System.err
  private[this] val originalIn = System.in
  private[sbt] class WriteableInputStream(in: InputStream, name: String)
      extends SimpleInputStream
      with AutoCloseable {
    private[this] val isRaw = new AtomicBoolean(false)
    final def write(bytes: Int*): Unit = buffer.synchronized {
      bytes.foreach(b => buffer.put(b))
    }
    def setRawMode(toggle: Boolean): Unit = {
      isRaw.set(toggle)
      in match {
        case win: WindowsInputStream => win.setRawMode(toggle)
        case _                       =>
      }
    }
    private[this] val executor =
      Executors.newSingleThreadExecutor(r => new Thread(r, s"sbt-$name-input-reader"))
    private[this] val buffer = new LinkedBlockingQueue[Integer]
    private[this] val closed = new AtomicBoolean(false)
    private[this] val readQueue = new LinkedBlockingQueue[Unit]
    private[this] val readThread = new AtomicReference[Thread]
    /*
     * Starts a loop that fills a buffer with bytes from stdin. We only read from
     * the underlying stream when the buffer is empty and there is an active reader.
     * If the reader detaches without consuming any bytes, we just buffer the
     * next byte that we read from the stream. One known issue with this approach
     * is that if a remote client triggers a reboot, we cannot necessarily stop this
     * loop from consuming the next byte from standard in even if sbt has fully
     * rebooted and the byte will never be consumed. We try to fix this in withStreams
     * by setting the terminal to raw mode, which the input stream makes it non blocking,
     * but this approach only works on posix platforms.
     */
    private[this] val runnable: Runnable = () => {
      @tailrec def impl(): Unit = {
        val _ = readQueue.take
        val b = in.read
        buffer.synchronized(buffer.put(b))
        if (Thread.interrupted() || (b == -1 && isRaw.get)) closed.set(true)
        else impl()
      }
      try impl()
      catch { case _: InterruptedException => closed.set(true) }
    }
    executor.submit(runnable)
    def read(result: LinkedBlockingQueue[Integer]): Unit =
      if (!closed.get)
        readThread.synchronized {
          readThread.set(Thread.currentThread)
          try buffer.poll match {
            case null =>
              readQueue.put(())
              result.put(buffer.take)
            case b if b == -1 => throw new ClosedChannelException
            case b            => result.put(b)
          } finally readThread.set(null)
        }
    override def read(): Int = {
      val result = new LinkedBlockingQueue[Integer]
      read(result)
      result.poll match {
        case null => -1
        case i    => i.toInt
      }
    }
    def cancel(): Unit = readThread.synchronized {
      Option(readThread.getAndSet(null)).foreach(_.interrupt())
      readQueue.clear()
    }

    override def available(): Int = {
      buffer.size
    }
    override def close(): Unit = if (closed.compareAndSet(false, true)) {
      executor.shutdownNow()
      ()
    }
  }
  private[this] def nonBlockingIn(term: org.jline.terminal.Terminal): WriteableInputStream = {
    val in = if (Util.isNonCygwinWindows) new WindowsInputStream(term, originalIn) else originalIn
    new WriteableInputStream(in, "console")
  }

  private[this] val inputStream = new AtomicReference[InputStream](System.in)
  private[this] def withOut[T](f: => T): T = {
    try {
      System.setOut(proxyPrintStream)
      System.setErr(proxyErrorStream)
      scala.Console.withErr(proxyErrorStream)(scala.Console.withOut(proxyPrintStream)(f))
    } finally {
      System.setOut(originalOut)
      System.setErr(originalErr)
    }
  }
  private[this] def withIn[T](f: => T): T =
    try {
      inputStream.set(proxyInputStream)
      System.setIn(proxyInputStream)
      scala.Console.withIn(proxyInputStream)(f)
    } finally System.setIn(originalIn)

  private[sbt] def withPrintStream[T](f: PrintStream => T): T = console.withPrintStream(f)
  private[this] val attached = new AtomicBoolean(true)

  /**
   * A wrapped instance of a jline.Terminal2 instance. It should only ever be changed when the
   * backgrounds sbt with ctrl+z and then foregrounds sbt which causes a call to reset. The
   * Terminal.console method returns this terminal and the ConsoleChannel delegates its
   * terminal method to it.
   */
  private[this] val consoleTerminalHolder: AtomicReference[Terminal] =
    new AtomicReference(SimpleTerminal)

  /**
   * The terminal that is currently being used by the proxyInputStream and proxyOutputStream.
   * It is set through the Terminal.set method which is called by the SetTerminal command, which
   * is used to change the terminal during task evaluation. This allows us to route System.in and
   * System.out through the terminal's input and output streams.
   */
  private[this] val activeTerminal = new AtomicReference[Terminal](consoleTerminalHolder.get)

  /**
   * The boot input stream allows a remote client to forward input to the sbt process while
   * it is still loading. It works by updating proxyInputStream to read from the
   * value of bootInputStreamHolder if it is non-null as well as from the normal process
   * console io (assuming there is console io).
   */
  private[this] val bootInputStreamHolder = new AtomicReference[InputStream]

  /**
   * The boot output stream allows sbt to relay the bytes written to stdout to one or
   * more remote clients while the sbt build is loading and hasn't yet loaded a server.
   * The output stream of TerminalConsole is updated to write to value of
   * bootOutputStreamHolder when it is non-null as well as the normal process console
   * output stream.
   */
  private[this] val bootOutputStreamHolder = new AtomicReference[OutputStream]
  private[sbt] def setBootStreams(
      bootInputStream: InputStream,
      bootOutputStream: OutputStream
  ): Unit = {
    bootInputStreamHolder.set(bootInputStream)
    bootOutputStreamHolder.set(bootOutputStream)
  }

  private[sbt] trait SimpleInputStream extends InputStream {
    override def read(b: Array[Byte]): Int = read(b, 0, b.length)
    override def read(b: Array[Byte], off: Int, len: Int): Int = {
      read() match {
        case -1 => -1
        case byte =>
          b(off) = byte.toByte
          1
      }
    }
  }
  private[this] object proxyInputStream extends SimpleInputStream {
    private[this] val isScripted = System.getProperty("sbt.scripted", "false") == "true"
    /*
     * This is to handle the case when a remote client starts sbt and the build fails.
     * We need to be able to consume input bytes from the remote client, but they
     * haven't yet connected to the main server but may be connected to the
     * BootServerSocket. Unfortunately there is no poll method on input stream that
     * takes a duration so we have to manually implement that here. All of the input
     * streams that we create in sbt are interruptible, so we can just poll each
     * of the input streams and periodically interrupt the thread to switch between
     * the two input streams.
     */
    private class ReadThread extends Thread with AutoCloseable {
      val result = new LinkedBlockingQueue[Integer]
      val running = new AtomicBoolean(true)
      setDaemon(true)
      start()
      override def run(): Unit = while (running.get) {
        bootInputStreamHolder.get match {
          case null =>
          case is =>
            def readFrom(inputStream: InputStream) =
              try {
                if (running.get) {
                  inputStream.read match {
                    case -1 =>
                    case `NO_BOOT_CLIENTS_CONNECTED` =>
                      if (System.console == null) {
                        result.put(-1)
                        running.set(false)
                      }
                    case i =>
                      result.put(i)
                      running.set(false)
                  }
                }
              } catch { case _: InterruptedException => }
            readFrom(is)
            readFrom(activeTerminal.get().inputStream)
        }
      }
      override def close(): Unit = if (running.compareAndSet(true, false)) this.interrupt()
    }
    def read(): Int = {
      if (isScripted) -1
      else if (bootInputStreamHolder.get == null) activeTerminal.get().inputStream.read()
      else {
        val thread = new ReadThread
        @tailrec def poll(): Int = thread.result.poll(10, TimeUnit.MILLISECONDS) match {
          case null =>
            thread.interrupt()
            poll()
          case i => i
        }
        poll()
      }
    }
  }
  private[this] object proxyOutputStream extends OutputStream {
    private[this] def os: OutputStream = activeTerminal.get().outputStream
    def write(byte: Int): Unit = {
      os.write(byte)
      os.flush()
      if (byte == 10) os.flush()
    }
    override def write(bytes: Array[Byte]): Unit = write(bytes, 0, bytes.length)
    override def write(bytes: Array[Byte], offset: Int, len: Int): Unit = {
      os.write(bytes, offset, len)
      os.flush()
    }
    override def flush(): Unit = os.flush()
  }
  private[this] val proxyPrintStream = new LinePrintStream(proxyOutputStream) {
    override def toString: String = s"proxyPrintStream($proxyOutputStream)"
  }
  private[this] object proxyErrorOutputStream extends OutputStream {
    private[this] def os: OutputStream = activeTerminal.get().errorStream
    def write(byte: Int): Unit = os.write(byte)
    override def write(bytes: Array[Byte]): Unit = write(bytes, 0, bytes.length)
    override def write(bytes: Array[Byte], offset: Int, len: Int): Unit =
      os.write(bytes, offset, len)
    override def flush(): Unit = os.flush()
  }
  private[this] object proxyErrorStream extends PrintStream(proxyErrorOutputStream, true)
  private[this] lazy val isWindows =
    System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).indexOf("windows") >= 0
  private[this] object WrappedSystemIn extends SimpleInputStream {
    private[this] val in = proxyInputStream
    override def available(): Int = if (attached.get) in.available() else 0
    override def read(): Int = synchronized {
      if (attached.get) {
        val res = in.read()
        if (res == -1) attached.set(false)
        res
      } else -1
    }
  }

  /*
   * When the server is booted by a remote client, it may not be able to accurately
   * calculate the terminal properties. To work around this, we can set the
   * properties via an environment property. It was too difficult to get system
   * properties working correctly with windows.
   */
  private class Props(
      val width: Int,
      val height: Int,
      val ansi: Boolean,
      val color: Boolean,
      val supershell: Boolean
  )
  private[sbt] val TERMINAL_PROPS = "SBT_TERMINAL_PROPS"
  private val props = System.getenv(TERMINAL_PROPS) match {
    case null => None
    case p =>
      p.split(",") match {
        case Array(width, height, ansi, color, supershell) =>
          Try(
            new Props(
              width.toInt,
              height.toInt,
              ansi.toBoolean,
              color.toBoolean,
              supershell.toBoolean
            )
          ).toOption
        case _ => None
      }
  }
  private[sbt] def startedByRemoteClient = props.isDefined

  private[this] def newConsoleTerminal(): Terminal = {
    val system = JLine3.system
    new ConsoleTerminal(nonBlockingIn(system), originalOut, system)
  }

  private[sbt] def reset(): Unit = {
    jline.TerminalFactory.reset()
    console.close()
    if (hasConsole && !isDumbTerminal) consoleTerminalHolder.set(newConsoleTerminal())
  }

  // translate explicit class names to type in order to support
  //  older Scala, since it shaded classes but not the system property
  private[this] def fixTerminalProperty(): Unit = {
    val terminalProperty = "jline.terminal"
    val newValue =
      if (!isAnsiSupported && System.getProperty("sbt.io.virtual", "") == "false") "none"
      else
        System.getProperty(terminalProperty) match {
          case "jline.UnixTerminal"                             => "unix"
          case null if System.getProperty("sbt.cygwin") != null => "unix"
          case "jline.WindowsTerminal"                          => "windows"
          case "jline.AnsiWindowsTerminal"                      => "windows"
          case "jline.UnsupportedTerminal"                      => "none"
          case null if isDumb                                   => "none"
          case x                                                => x
        }
    if (newValue != null) {
      System.setProperty(terminalProperty, newValue)
      ()
    }
  }
  fixTerminalProperty()

  private[sbt] def createReader(term: Terminal, prompt: Prompt): ConsoleReader = {
    new ConsoleReader(term.inputStream, term.outputStream, term.toJLine) {
      override def readLine(prompt: String, mask: Character): String =
        term.withRawInput(super.readLine(prompt, mask))
      override def readLine(prompt: String): String = term.withRawInput(super.readLine(prompt))
    }
  }

  def console: Terminal = consoleTerminalHolder.get match {
    case null => throw new IllegalStateException("Uninitialized terminal.")
    case term => term
  }

  private val capabilityMap =
    org.jline.utils.InfoCmp.Capability.values().map(c => c.toString -> c).toMap
  private val consoleProgressState = new AtomicReference[ProgressState](new ProgressState(1))
  private[sbt] def setConsoleProgressState(progressState: ProgressState): Unit =
    consoleProgressState.set(progressState)

  @deprecated("For compatibility only", "1.4.0")
  private[sbt] def deprecatedTeminal: jline.Terminal = console.toJLine
  private[util] class ConsoleTerminal(
      in: WriteableInputStream,
      out: OutputStream,
      private[util] val system: org.jline.terminal.Terminal,
  ) extends TerminalImpl(in, out, originalErr, "console0") {
    private[this] val rawMode = new AtomicBoolean(false)
    if (Util.isWindows && hasConsole) {
      // It is necessary to enter and exit raw mode in order to get the windows
      // console to echo input.
      enterRawMode()
      exitRawMode()
    }
    override private[sbt] def getSizeImpl: (Int, Int) = {
      val size = system.getSize
      (size.getColumns, size.getRows)
    }
    override lazy val isAnsiSupported: Boolean =
      !isDumbTerminal && Terminal.isAnsiSupported && !isCI
    override private[sbt] def progressState: ProgressState = consoleProgressState.get
    override def isEchoEnabled: Boolean =
      try system.echo()
      catch { case _: InterruptedIOException => false }
    override def isSuccessEnabled: Boolean = true
    override def setEchoEnabled(toggle: Boolean): Unit =
      try Util.ignoreResult(system.echo(toggle))
      catch { case _: InterruptedIOException => }
    override def getBooleanCapability(capability: String): Boolean =
      capabilityMap.get(capability).fold(false)(system.getBooleanCapability)
    override def getNumericCapability(capability: String): Integer =
      capabilityMap.get(capability).fold(null: Integer)(system.getNumericCapability)
    override def getStringCapability(capability: String): String = {
      val res = capabilityMap.get(capability).fold(null: String)(system.getStringCapability)
      res
    }
    override private[sbt] def restore(): Unit = exitRawMode()

    override private[sbt] def getAttributes: Map[String, String] =
      Try(JLine3.toMap(system.getAttributes)).getOrElse(Map.empty)
    override private[sbt] def setAttributes(attributes: Map[String, String]): Unit = {
      system.setAttributes(JLine3.attributesFromMap(attributes))
      JLine3.setEnableProcessInput()
    }
    override private[sbt] def setSize(width: Int, height: Int): Unit =
      system.setSize(new org.jline.terminal.Size(width, height))

    override def inputStream: InputStream = in

    override private[sbt] def enterRawMode(): Unit =
      if (rawMode.compareAndSet(false, true) && hasConsole) {
        in.setRawMode(true)
        try JLine3.enterRawMode(system)
        catch { case _: java.io.IOError => }
      }
    override private[sbt] def exitRawMode(): Unit =
      if (rawMode.compareAndSet(true, false) && hasConsole) {
        in.setRawMode(false)
        try JLine3.exitRawMode(system)
        catch { case _: java.io.IOError => }
      }
    override def isColorEnabled: Boolean =
      props
        .map(_.color)
        .getOrElse(isColorEnabledProp.getOrElse(Terminal.isColorEnabled))

    override def isSupershellEnabled: Boolean =
      props
        .map(_.supershell)
        .getOrElse(System.getProperty("sbt.supershell") match {
          case null =>
            !(sys.env.contains("BUILD_NUMBER") || sys.env
              .contains("CI")) && isColorEnabled && !Util.isEmacs
          case "true" => true
          case _      => false
        })
    override def close(): Unit = {
      try {
        system.setAttributes(JLine3.initialAttributes.get)
        system.close()
        in.close()
      } catch { case NonFatal(_) => }
      super.close()
    }
  }
  private[sbt] abstract class TerminalImpl private[sbt] (
      val in: WriteableInputStream,
      val out: OutputStream,
      override val errorStream: OutputStream,
      override private[sbt] val name: String
  ) extends Terminal {
    private[sbt] def getSizeImpl: (Int, Int)
    private[this] val sizeRefreshPeriod = 1.second
    private[this] val size =
      new AtomicReference[((Int, Int), Deadline)](((1, 1), Deadline.now - 1.day))
    private[this] def setSize() = size.set((Try(getSizeImpl).getOrElse((1, 1)), Deadline.now))
    private[this] def getSize = size.get match {
      case (s, d) if (d + sizeRefreshPeriod).isOverdue =>
        setSize()
        size.get._1
      case (s, _) => s
    }
    override def getWidth: Int = getSize._1
    override def getHeight: Int = getSize._2
    private[this] val rawMode = new AtomicBoolean(false)
    private[this] val writeLock = new AnyRef
    def throwIfClosed[R](f: => R): R = if (isStopped.get) throw new ClosedChannelException else f
    override def getLastLine: Option[String] = progressState.currentLine
    override def getLines: Seq[String] = progressState.getLines

    private val combinedOutputStream = new OutputStream {
      override def write(b: Int): Unit = {
        Option(bootOutputStreamHolder.get).foreach(_.write(b))
        out.write(b)
      }
      override def write(b: Array[Byte]): Unit = {
        write(b, 0, b.length)
      }
      override def write(b: Array[Byte], offset: Int, len: Int): Unit = {
        Option(bootOutputStreamHolder.get).foreach(_.write(b, offset, len))
        out.write(b, offset, len)
      }
      override def flush(): Unit = {
        Option(bootOutputStreamHolder.get).foreach(_.flush())
        out.flush()
      }
    }

    override val outputStream = new OutputStream {
      override def write(b: Int): Unit = throwIfClosed {
        write(Array((b & 0xFF).toByte))
      }
      override def write(b: Array[Byte]): Unit = throwIfClosed {
        writeLock.synchronized(doWrite(b))
      }
      override def write(b: Array[Byte], offset: Int, length: Int): Unit = throwIfClosed {
        write(Arrays.copyOfRange(b, offset, offset + length))
      }
      override def flush(): Unit = combinedOutputStream.flush()
    }
    private def doWrite(rawBytes: Array[Byte]): Unit = withPrintStream { ps =>
      val (toWrite, len) =
        if (rawBytes.contains(27.toByte)) {
          if (!isAnsiSupported || !isColorEnabled)
            EscHelpers.strip(rawBytes, stripAnsi = !isAnsiSupported, stripColor = !isColorEnabled)
          else (rawBytes, rawBytes.length)
        } else (rawBytes, rawBytes.length)
      val bytes = if (len < toWrite.length) toWrite.take(len) else toWrite
      progressState.write(TerminalImpl.this, bytes, ps, hasProgress.get && !rawMode.get)
    }
    override private[sbt] val printStream: PrintStream = new LinePrintStream(outputStream)
    override def inputStream: InputStream = in

    private[sbt] def write(bytes: Int*): Unit = in.write(bytes: _*)
    private[this] val isStopped = new AtomicBoolean(false)

    override def getLineHeightAndWidth(line: String): (Int, Int) = getWidth match {
      case width if width > 0 =>
        val position = EscHelpers.cursorPosition(line)
        val count = (position + width - 1) / width
        (count, position - (math.max((count - 1), 0) * width))
      case _ => (0, 0)
    }

    private[sbt] def withRawOutput[R](f: => R): R = {
      rawMode.set(true)
      try f
      finally rawMode.set(false)
    }
    private[this] val rawPrintStream: PrintStream = new LinePrintStream(combinedOutputStream)
    override def withPrintStream[T](f: PrintStream => T): T =
      writeLock.synchronized(f(rawPrintStream))

    override def close(): Unit = if (isStopped.compareAndSet(false, true)) {
      in.close()
    }
  }
  private lazy val nullInputStream: InputStream = () => {
    try this.synchronized(this.wait)
    catch { case _: InterruptedException => }
    -1
  }
  private[sbt] class DefaultTerminal extends Terminal {
    override def close(): Unit = {}
    override private[sbt] def progressState: ProgressState = new ProgressState(1)
    override private[sbt] def enterRawMode(): Unit = {}
    override private[sbt] def exitRawMode(): Unit = {}
    override def getBooleanCapability(capability: String): Boolean = false
    override def getHeight: Int = 0
    override def getLastLine: Option[String] = None
    override def getLines: Seq[String] = Nil
    override def getLineHeightAndWidth(line: String): (Int, Int) = (0, 0)
    override def getNumericCapability(capability: String): Integer = null
    override def getStringCapability(capability: String): String = null
    override def getWidth: Int = 0
    override def inputStream: InputStream = nullInputStream
    override def isAnsiSupported: Boolean = Terminal.isAnsiSupported
    override def isColorEnabled: Boolean = isColorEnabledProp.getOrElse(Terminal.isColorEnabled)
    override def isEchoEnabled: Boolean = false
    override def isSuccessEnabled: Boolean = true
    override def isSupershellEnabled: Boolean = false
    override def setEchoEnabled(toggle: Boolean): Unit = {}
    override def outputStream: OutputStream = _ => {}
    override def errorStream: OutputStream = _ => {}
    override private[sbt] def getAttributes: Map[String, String] = Map.empty
    override private[sbt] def setAttributes(attributes: Map[String, String]): Unit = {}
    override private[sbt] def setSize(width: Int, height: Int): Unit = {}
    override private[sbt] def name: String = "NullTerminal"
    override private[sbt] val printStream: PrintStream =
      new PrintStream(outputStream, false)
    override private[sbt] def withPrintStream[T](f: PrintStream => T): T = f(printStream)
    override private[sbt] def write(bytes: Int*): Unit = {}
    override private[sbt] def withRawOutput[R](f: => R): R = f
  }
  private[sbt] object NullTerminal extends DefaultTerminal
  private[sbt] object SimpleTerminal extends DefaultTerminal {
    override lazy val inputStream: InputStream = originalIn
    override lazy val outputStream: OutputStream = originalOut
    override lazy val errorStream: OutputStream = originalErr
  }
}
