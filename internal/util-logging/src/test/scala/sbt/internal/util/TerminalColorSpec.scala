/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import org.scalatest.flatspec.AnyFlatSpec
import java.io.{ ByteArrayOutputStream, InputStream }

class TerminalColorSpec extends AnyFlatSpec {
  private def createTerminal(
      colorEnabled: Boolean,
      ansiSupported: Boolean,
      out: ByteArrayOutputStream
  ): Terminal.TerminalImpl =
    new Terminal.TerminalImpl(
      new Terminal.WriteableInputStream(new InputStream { def read() = -1 }, "test"),
      out,
      new ByteArrayOutputStream(),
      "test"
    ) {
      private[sbt] def getSizeImpl: (Int, Int) = (80, 24)
      override def isColorEnabled: Boolean = colorEnabled
      override def isAnsiSupported: Boolean = ansiSupported
      override private[sbt] def progressState: ProgressState = new ProgressState(1)
      override def isSuccessEnabled: Boolean = true
      override def isSupershellEnabled: Boolean = false
      override def isEchoEnabled: Boolean = true
      override def setEchoEnabled(toggle: Boolean): Unit = ()
      override def getBooleanCapability(capability: String): Boolean = false
      override def getNumericCapability(capability: String): Integer = null
      override def getStringCapability(capability: String): String = null
      override private[sbt] def getAttributes: Map[String, String] = Map.empty
      override private[sbt] def setAttributes(attributes: Map[String, String]): Unit = ()
      override private[sbt] def setSize(width: Int, height: Int): Unit = ()
      override private[sbt] def enterRawMode(): Unit = ()
      override private[sbt] def exitRawMode(): Unit = ()
    }

  private val ESC = "\u001b"
  private val coloredText = s"$ESC[31mred text$ESC[0m"

  "Terminal with colors disabled" should "strip color codes from output" in {
    val out = new ByteArrayOutputStream()
    val term = createTerminal(colorEnabled = false, ansiSupported = true, out)
    term.outputStream.write(coloredText.getBytes("UTF-8"))
    term.outputStream.flush()
    val output = out.toString("UTF-8")
    assert(!output.contains(ESC))
    assert(output.contains("red text"))
  }

  "Terminal with colors enabled" should "preserve color codes in output" in {
    val out = new ByteArrayOutputStream()
    val term = createTerminal(colorEnabled = true, ansiSupported = true, out)
    term.outputStream.write(coloredText.getBytes("UTF-8"))
    term.outputStream.flush()
    val output = out.toString("UTF-8")
    assert(output.contains(ESC))
    assert(output.contains("red text"))
  }
}
