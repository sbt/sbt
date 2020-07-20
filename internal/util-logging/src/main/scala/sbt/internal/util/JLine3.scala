/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import java.io.{ EOFException, InputStream, OutputStream, PrintWriter }
import java.nio.charset.Charset
import java.util.{ Arrays, EnumSet }
import java.util.concurrent.atomic.AtomicBoolean
import org.jline.utils.InfoCmp.Capability
import org.jline.utils.{ NonBlocking, OSUtils }
import org.jline.terminal.{ Attributes, Size, Terminal => JTerminal }
import org.jline.terminal.Terminal.SignalHandler
import org.jline.terminal.impl.AbstractTerminal
import org.jline.terminal.impl.jansi.JansiSupportImpl
import org.jline.terminal.impl.jansi.win.JansiWinSysTerminal
import scala.collection.JavaConverters._
import scala.util.Try

private[util] object JLine3 {
  def getJLine3Terminal: JTerminal = apply(Terminal.get)
  private val capabilityMap = Capability
    .values()
    .map { c =>
      c.toString -> c
    }
    .toMap

  private[util] def system = {
    /*
     * For reasons that are unclear to me, TerminalBuilder fails to build
     * windows terminals. The instructions about the classpath did not work:
     * https://stackoverflow.com/questions/52851232/jline3-issues-with-windows-terminal
     * We can deconstruct what TerminalBuilder does and inline it for now.
     * It is possible that this workaround will break WSL but I haven't checked that.
     */
    if (Util.isNonCygwinWindows) {
      val support = new JansiSupportImpl
      val winConsole = support.isWindowsConsole();
      try {
        val term = JansiWinSysTerminal.createTerminal(
          "console",
          "ansi",
          OSUtils.IS_CONEMU,
          Charset.forName("UTF-8"),
          -1,
          false,
          SignalHandler.SIG_DFL,
          true
        )
        term.disableScrolling()
        term
      } catch {
        case _: Exception =>
          org.jline.terminal.TerminalBuilder
            .builder()
            .system(false)
            .paused(true)
            .jansi(true)
            .streams(Terminal.console.inputStream, Terminal.console.outputStream)
            .build()
      }
    } else {
      org.jline.terminal.TerminalBuilder
        .builder()
        .system(System.console != null)
        .paused(true)
        .jna(false)
        .jansi(true)
        .build()
    }
  }
  private[sbt] def apply(term: Terminal): JTerminal = {
    new AbstractTerminal(term.name, "ansi", Charset.forName("UTF-8"), SignalHandler.SIG_DFL) {
      val closed = new AtomicBoolean(false)
      setOnClose { () =>
        if (closed.compareAndSet(false, true)) {
          // This is necessary to shutdown the non blocking input reader
          // so that it doesn't keep blocking
          term.inputStream match {
            case w: Terminal.WriteableInputStream => w.cancel()
            case _                                =>
          }
        }
      }
      parseInfoCmp()
      override val input: InputStream = new InputStream {
        override def read: Int = {
          val res = try term.inputStream.read
          catch { case _: InterruptedException => -2 }
          if (res == 4 && term.prompt.render().endsWith(term.prompt.mkPrompt()))
            throw new EOFException
          res
        }
      }
      override val output: OutputStream = new OutputStream {
        override def write(b: Int): Unit = write(Array[Byte](b.toByte))
        override def write(b: Array[Byte]): Unit = if (!closed.get) term.withPrintStream { ps =>
          term.prompt match {
            case a: Prompt.AskUser => a.write(b)
            case _                 =>
          }
          ps.write(b)
        }
        override def write(b: Array[Byte], offset: Int, len: Int) =
          write(Arrays.copyOfRange(b, offset, offset + len))
        override def flush(): Unit = term.withPrintStream(_.flush())
      }

      override val reader =
        NonBlocking.nonBlocking(term.name, input, Charset.defaultCharset())
      override val writer: PrintWriter = new PrintWriter(output, true)
      /*
       * For now assume that the terminal capabilities for client and server
       * are the same.
       */
      override def getStringCapability(cap: Capability): String = {
        term.getStringCapability(cap.toString, jline3 = true)
      }
      override def getNumericCapability(cap: Capability): Integer = {
        term.getNumericCapability(cap.toString, jline3 = true)
      }
      override def getBooleanCapability(cap: Capability): Boolean = {
        term.getBooleanCapability(cap.toString, jline3 = true)
      }
      def getAttributes(): Attributes = attributesFromMap(term.getAttributes)
      def getSize(): Size = new Size(term.getWidth, term.getHeight)
      def setAttributes(a: Attributes): Unit = term.setAttributes(toMap(a))
      def setSize(size: Size): Unit = term.setSize(size.getColumns, size.getRows)

      /**
       * Override enterRawMode because the default implementation modifies System.in
       * to be non-blocking which means it immediately returns -1 if there is no
       * data available, which is not desirable for us.
       */
      override def enterRawMode(): Attributes = enterRawModeImpl(this)
    }
  }
  private def enterRawModeImpl(term: JTerminal): Attributes = {
    val prvAttr = term.getAttributes()
    val newAttr = new Attributes(prvAttr)
    newAttr.setLocalFlags(
      EnumSet
        .of(Attributes.LocalFlag.ICANON, Attributes.LocalFlag.ECHO, Attributes.LocalFlag.IEXTEN),
      false
    )
    newAttr.setInputFlags(
      EnumSet
        .of(Attributes.InputFlag.IXON, Attributes.InputFlag.ICRNL, Attributes.InputFlag.INLCR),
      false
    )
    term.setAttributes(newAttr)
    prvAttr
  }
  private[util] def enterRawMode(term: JTerminal): Map[String, String] =
    toMap(enterRawModeImpl(term))
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
  private[this] val iflagMap: Map[String, Attributes.InputFlag] =
    Attributes.InputFlag.values.map(f => f.name.toLowerCase -> f).toMap
  private[this] val oflagMap: Map[String, Attributes.OutputFlag] =
    Attributes.OutputFlag.values.map(f => f.name.toLowerCase -> f).toMap
  private[this] val cflagMap: Map[String, Attributes.ControlFlag] =
    Attributes.ControlFlag.values.map(f => f.name.toLowerCase -> f).toMap
  private[this] val lflagMap: Map[String, Attributes.LocalFlag] =
    Attributes.LocalFlag.values.map(f => f.name.toLowerCase -> f).toMap
  private[this] val charMap: Map[String, Attributes.ControlChar] =
    Attributes.ControlChar.values().map(f => f.name.toLowerCase -> f).toMap
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
}
