/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import java.io.{ ByteArrayOutputStream, InputStream }
import verify.BasicTestSuite

object TerminalColorSpec extends BasicTestSuite:
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
    ):
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

  private val ESC: String = "\u001b"
  private val coloredText: String = s"$ESC[31mred text$ESC[0m"

  test("Terminal with colors disabled should strip color codes from output"):
    val out = new ByteArrayOutputStream()
    val term = createTerminal(colorEnabled = false, ansiSupported = true, out)
    term.outputStream.write(coloredText.getBytes("UTF-8"))
    term.outputStream.flush()
    val output = out.toString("UTF-8")
    assert(!output.contains(ESC))
    assert(output.contains("red text"))

  test("Terminal with colors enabled should preserve color codes in output"):
    val out = new ByteArrayOutputStream()
    val term = createTerminal(colorEnabled = true, ansiSupported = true, out)
    term.outputStream.write(coloredText.getBytes("UTF-8"))
    term.outputStream.flush()
    val output = out.toString("UTF-8")
    assert(output.contains(ESC))
    assert(output.contains("red text"))

  // isColorDefault pure function tests

  private def colorDefault(
      propsColor: Option[Boolean] = None,
      colorProp: Option[Boolean] = None,
      logFormatOpt: Option[Boolean] = None,
      hasConsole: Boolean = false,
      isDumbTerminal: Boolean = false,
      isCI: Boolean = false,
      isEmacs: Boolean = false,
  ): Boolean =
    Terminal.isColorDefault(
      propsColor,
      colorProp,
      logFormatOpt,
      hasConsole,
      isDumbTerminal,
      isCI,
      isEmacs
    )

  test("isColorDefault should use propsColor when set"):
    assert(colorDefault(propsColor = Some(true), colorProp = Some(false), hasConsole = false))
    assert(!colorDefault(propsColor = Some(false), colorProp = Some(true), hasConsole = true))

  test("isColorDefault should fall back to colorProp when propsColor is None"):
    assert(colorDefault(colorProp = Some(true), hasConsole = false))
    assert(!colorDefault(colorProp = Some(false), hasConsole = true))

  test("isColorDefault should use logFormatOpt when props and colorProp are None"):
    // logFormatOpt = Some(false) vetoes color even with console and CI
    assert(!colorDefault(logFormatOpt = Some(false), hasConsole = true, isCI = true))
    // logFormatOpt = Some(true) allows color when terminal heuristic is positive
    assert(colorDefault(logFormatOpt = Some(true), hasConsole = true))

  test("isColorDefault should enable color for interactive console"):
    assert(colorDefault(hasConsole = true))

  test("isColorDefault should disable color for dumb terminal without CI"):
    assert(!colorDefault(hasConsole = true, isDumbTerminal = true))

  test("isColorDefault should enable color on CI"):
    assert(colorDefault(isCI = true))

  test("isColorDefault should enable color in Emacs"):
    assert(colorDefault(isEmacs = true))

  test("isColorDefault should disable color when no console, no CI, no Emacs"):
    assert(!colorDefault())
end TerminalColorSpec
