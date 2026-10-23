/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import hedgehog._
import hedgehog.runner._
import scala.util.Try

object ConsoleOutSpec extends Properties {
  override def tests: List[Test] = List(
    property(
      "terminalOut drops output for a closed terminal",
      genLine.forAll.map { s =>
        val term = terminal(new ByteArrayOutputStream())
        term.close()
        withActive(term) {
          val out = ConsoleOut.terminalOut
          Result.assert(Try { out.print(s); out.println(s); out.println(); out.flush() }.isSuccess)
        }
      }
    ),
    property(
      "terminalOut writes to an open terminal",
      genLine.forAll.map { s =>
        val bytes = new ByteArrayOutputStream()
        val term = terminal(bytes)
        try withActive(term) {
          ConsoleOut.terminalOut.println(s)
          ConsoleOut.terminalOut.flush()
          Result.assert(new String(bytes.toByteArray, UTF_8).contains(s))
        } finally term.close()
      }
    ),
    property(
      "systemOutOverwrite drops output when System.out is a closed terminal",
      genLine.forAll.map { s =>
        val term = terminal(new ByteArrayOutputStream())
        term.close()
        val original = System.out
        System.setOut(term.printStream)
        try {
          val out = ConsoleOut.systemOutOverwrite((_, _) => false)
          Result.assert(Try { out.print(s); out.println(s); out.flush() }.isSuccess)
        } finally System.setOut(original)
      }
    ),
  )

  private val genLine: Gen[String] = Gen.string(Gen.alphaNum, Range.linear(1, 40))

  private def withActive[A](term: Terminal)(f: => A): A = {
    val previous = Terminal.set(term)
    try f
    finally {
      Terminal.set(previous)
      ()
    }
  }

  private def terminal(out: ByteArrayOutputStream): Terminal.TerminalImpl =
    new Terminal.TerminalImpl(
      new Terminal.WriteableInputStream(() => -1, "test"),
      out,
      new ByteArrayOutputStream(),
      "test"
    ) {
      private[sbt] def getSizeImpl: (Int, Int) = (80, 24)
      override def isColorEnabled: Boolean = false
      override def isAnsiSupported: Boolean = false
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
}
