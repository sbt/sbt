/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import java.io.{ InputStream, OutputStream, PrintWriter }
import java.nio.charset.Charset
import java.util.{ Arrays, EnumSet }
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }
import org.jline.utils.InfoCmp.Capability
import org.jline.utils.{ ClosedException, NonBlockingReader }
import org.jline.terminal.{ Attributes, Size, Terminal => JTerminal }
import org.jline.terminal.Attributes.{ InputFlag, LocalFlag }
import org.jline.terminal.Terminal.SignalHandler
import org.jline.terminal.impl.{ AbstractTerminal, DumbTerminal }
import org.jline.terminal.impl.jansi.JansiSupportImpl
import org.jline.terminal.impl.jansi.win.JansiWinSysTerminal
import org.jline.utils.OSUtils
import scala.collection.JavaConverters._
import scala.util.Try
import java.util.concurrent.LinkedBlockingQueue
import org.fusesource.jansi.internal.WindowsSupport

private[sbt] object JLine3 {
  private[util] val initialAttributes = new AtomicReference[Attributes]

  private[this] val forceWindowsJansiHolder = new AtomicBoolean(false)
  private[sbt] def forceWindowsJansi(): Unit = forceWindowsJansiHolder.set(true)
  private[this] def windowsJansi(): org.jline.terminal.Terminal = {
    val support = new JansiSupportImpl
    val winConsole = support.isWindowsConsole();
    val termType = sys.props.get("org.jline.terminal.type").orElse(sys.env.get("TERM")).orNull
    val term = JansiWinSysTerminal.createTerminal(
      "console",
      termType,
      OSUtils.IS_CONEMU,
      Charset.forName("UTF-8"),
      -1,
      false,
      SignalHandler.SIG_DFL,
      true
    )
    term.disableScrolling()
    term
  }
  private val jansi = {
    val (major, minor) =
      (JansiSupportImpl.getJansiMajorVersion, JansiSupportImpl.getJansiMinorVersion)
    (major > 1 || minor >= 18) && Util.isWindows
  }
  private[util] def system: org.jline.terminal.Terminal = {
    val term =
      if (forceWindowsJansiHolder.get) windowsJansi()
      else {
        // Only use jna on windows. Both jna and jansi use illegal reflective
        // accesses on posix system.
        org.jline.terminal.TerminalBuilder
          .builder()
          .system(System.console != null)
          .jna(Util.isWindows && !jansi)
          .jansi(jansi)
          .paused(true)
          .build()
      }
    initialAttributes.get match {
      case null => initialAttributes.set(term.getAttributes)
      case _    =>
    }
    term
  }
  private[sbt] def apply(term: Terminal): JTerminal = {
    if (System.getProperty("jline.terminal", "") == "none")
      new DumbTerminal(term.inputStream, term.outputStream)
    else wrapTerminal(term)
  }
  private[this] def wrapTerminal(term: Terminal): JTerminal = {
    new AbstractTerminal(
      term.name,
      "nocapabilities",
      Charset.forName("UTF-8"),
      SignalHandler.SIG_DFL
    ) {
      val closed = new AtomicBoolean(false)
      setOnClose { () =>
        doClose()
        reader.close()
        if (closed.compareAndSet(false, true)) {
          // This is necessary to shutdown the non blocking input reader
          // so that it doesn't keep blocking
          term.inputStream match {
            case w: Terminal.WriteableInputStream => w.cancel()
            case _                                =>
          }
        }
      }
      override val input: InputStream = new InputStream {
        override def read: Int = {
          val res = term.inputStream match {
            case w: Terminal.WriteableInputStream =>
              val result = new LinkedBlockingQueue[Integer]
              try {
                w.read(result)
                result.poll match {
                  case null => throw new ClosedException
                  case i    => i.toInt
                }
              } catch {
                case _: InterruptedException =>
                  w.cancel()
                  throw new ClosedException
              }
            case _ => throw new ClosedException
          }
          res match {
            case 3 /* ctrl+c */ => throw new ClosedException
            case r              => r
          }
        }
      }
      override val output: OutputStream = new OutputStream {
        override def write(b: Int): Unit = write(Array[Byte](b.toByte))
        override def write(b: Array[Byte]): Unit = if (!closed.get) term.withPrintStream { ps =>
          ps.write(b)
          term.prompt match {
            case a: Prompt.AskUser => a.write(b)
            case _                 =>
          }
        }
        override def write(b: Array[Byte], offset: Int, len: Int) =
          write(Arrays.copyOfRange(b, offset, offset + len))
        override def flush(): Unit = term.withPrintStream(_.flush())
      }

      override val reader = new NonBlockingReader {
        val buffer = new LinkedBlockingQueue[Integer]
        val thread = new AtomicReference[Thread]
        private def fillBuffer(): Unit = thread.synchronized {
          thread.set(Thread.currentThread)
          buffer.put(
            try input.read()
            catch { case _: InterruptedException => -3 }
          )
        }
        override def close(): Unit = thread.get match {
          case null =>
          case t    => t.interrupt()
        }
        override def read(timeout: Long, peek: Boolean) = {
          if (buffer.isEmpty && !peek) fillBuffer()
          (if (peek) buffer.peek else buffer.take) match {
            case null => -2
            case i    => if (i == -3) throw new ClosedException else i
          }
        }
        override def peek(timeout: Long): Int = buffer.peek() match {
          case null => -1
          case i    => i.toInt
        }
        override def readBuffered(buf: Array[Char]): Int = {
          if (buffer.isEmpty) fillBuffer()
          buffer.take match {
            case i if i == -1 => -1
            case i =>
              buf(0) = i.toChar
              1
          }
        }
      }
      override val writer: PrintWriter = new PrintWriter(output, true)
      /*
       * For now assume that the terminal capabilities for client and server
       * are the same.
       */
      override def getStringCapability(cap: Capability): String = {
        term.getStringCapability(cap.toString) match {
          case null if cap == Capability.key_dc && Util.isWindows  => "\\E[3~"
          case null if cap == Capability.key_end && Util.isWindows => "\\E[4~"
          case null if cap == Capability.key_ic && Util.isWindows  => "\\E[2~"
          case c                                                   => c
        }
      }
      override def getNumericCapability(cap: Capability): Integer =
        term.getNumericCapability(cap.toString)
      override def getBooleanCapability(cap: Capability): Boolean =
        term.getBooleanCapability(cap.toString)
      def getAttributes(): Attributes = attributesFromMap(term.getAttributes)
      def getSize(): Size = new Size(term.getWidth, term.getHeight)
      def setAttributes(a: Attributes): Unit = {} // don't allow the jline line reader to change attributes
      def setSize(size: Size): Unit = term.setSize(size.getColumns, size.getRows)

      override def enterRawMode(): Attributes = {
        // don't actually modify the term, that is handled by LineReader
        attributesFromMap(term.getAttributes)
      }
    }
  }
  private def enterRawModeImpl(term: JTerminal): Attributes = {
    val prvAttr = term.getAttributes()
    val newAttr = new Attributes(prvAttr)
    newAttr.setLocalFlags(EnumSet.of(LocalFlag.ICANON, LocalFlag.ECHO, LocalFlag.IEXTEN), false)
    newAttr.setInputFlags(EnumSet.of(InputFlag.IXON, InputFlag.ICRNL, InputFlag.INLCR), false)
    term.setAttributes(newAttr)
    prvAttr
  }
  // We need to set the ENABLE_PROCESS_INPUT flag for ctrl+c to be treated as a signal in windows
  // https://docs.microsoft.com/en-us/windows/console/setconsolemode
  private[this] val ENABLE_PROCESS_INPUT = 1
  private[util] def setEnableProcessInput(): Unit = if (Util.isWindows) {
    WindowsSupport.setConsoleMode(WindowsSupport.getConsoleMode | ENABLE_PROCESS_INPUT)
  }
  private[util] def enterRawMode(term: JTerminal): Unit = {
    val prevAttr = initialAttributes.get
    val newAttr = new Attributes(prevAttr)
    // These flags are copied from the jline3 enterRawMode but the jline implementation
    // also puts the input stream in non blocking mode, which we do not want.
    newAttr.setLocalFlags(EnumSet.of(LocalFlag.ICANON, LocalFlag.IEXTEN, LocalFlag.ECHO), false)
    newAttr.setInputFlags(EnumSet.of(InputFlag.IXON, InputFlag.ICRNL, InputFlag.INLCR), false)
    term.setAttributes(newAttr)
    setEnableProcessInput()
  }
  private[util] def exitRawMode(term: JTerminal): Unit = {
    val initAttr = initialAttributes.get
    val newAttr = new Attributes(initAttr)
    newAttr.setLocalFlags(EnumSet.of(LocalFlag.ICANON, LocalFlag.ECHO), true)
    term.setAttributes(newAttr)
    setEnableProcessInput()
  }
  private[util] def toMap(jattributes: Attributes): Map[String, String] = {
    val result = new java.util.LinkedHashMap[String, String]
    result.put(
      "iflag",
      jattributes.getInputFlags.iterator.asScala.map(_.name.toLowerCase).mkString(" ")
    )
    result.put(
      "oflag",
      jattributes.getOutputFlags.iterator.asScala.map(_.name.toLowerCase).mkString(" ")
    )
    result.put(
      "cflag",
      jattributes.getControlFlags.iterator.asScala.map(_.name.toLowerCase).mkString(" ")
    )
    result.put(
      "lflag",
      jattributes.getLocalFlags.iterator.asScala.map(_.name.toLowerCase).mkString(" ")
    )
    result.put(
      "cchars",
      jattributes.getControlChars.entrySet.iterator.asScala
        .map { e =>
          s"${e.getKey.name.toLowerCase},${e.getValue}"
        }
        .mkString(" ")
    )
    result.asScala.toMap
  }
  private[this] val iflagMap: Map[String, InputFlag] =
    InputFlag.values.map(f => f.name.toLowerCase -> f).toMap
  private[this] val oflagMap: Map[String, Attributes.OutputFlag] =
    Attributes.OutputFlag.values.map(f => f.name.toLowerCase -> f).toMap
  private[this] val cflagMap: Map[String, Attributes.ControlFlag] =
    Attributes.ControlFlag.values.map(f => f.name.toLowerCase -> f).toMap
  private[this] val lflagMap: Map[String, LocalFlag] =
    LocalFlag.values.map(f => f.name.toLowerCase -> f).toMap
  private[this] val charMap: Map[String, Attributes.ControlChar] =
    Attributes.ControlChar.values().map(f => f.name.toLowerCase -> f).toMap
  private[sbt] def setMode(term: Terminal, canonical: Boolean, echo: Boolean): Unit = {
    val prev = attributesFromMap(term.getAttributes)
    val newAttrs = new Attributes(prev)
    newAttrs.setLocalFlag(LocalFlag.ICANON, canonical)
    newAttrs.setLocalFlag(LocalFlag.ECHO, echo)
    term.setAttributes(toMap(newAttrs))
  }
  private[util] def attributesFromMap(map: Map[String, String]): Attributes = {
    val attributes = new Attributes
    map.get("iflag").foreach { flags =>
      flags.split(" ").foreach(f => iflagMap.get(f).foreach(attributes.setInputFlag(_, true)))
    }
    map.get("oflag").foreach { flags =>
      flags.split(" ").foreach(f => oflagMap.get(f).foreach(attributes.setOutputFlag(_, true)))
    }
    map.get("cflag").foreach { flags =>
      flags.split(" ").foreach(f => cflagMap.get(f).foreach(attributes.setControlFlag(_, true)))
    }
    map.get("lflag").foreach { flags =>
      flags.split(" ").foreach(f => lflagMap.get(f).foreach(attributes.setLocalFlag(_, true)))
    }
    map.get("cchars").foreach { chars =>
      chars.split(" ").foreach { keyValue =>
        keyValue.split(",") match {
          case Array(k, v) =>
            Try(v.toInt).foreach(i => charMap.get(k).foreach(c => attributes.setControlChar(c, i)))
          case _ =>
        }
      }
    }
    attributes
  }
  private[sbt] def isEchoEnabled(map: Map[String, String]): Boolean = {
    attributesFromMap(map).getLocalFlag(LocalFlag.ECHO)
  }
}
