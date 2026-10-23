/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.{ ByteArrayOutputStream, InputStream, OutputStream, PrintStream }
import sbt.internal.util.{ ProgressState, Terminal => ITerminal }
import hedgehog._
import hedgehog.runner._
import scala.collection.mutable.ListBuffer
import scala.util.{ Failure, Success, Try }

object TerminalSpec extends Properties {
  private final class ThunkFailure extends RuntimeException("thunk failure")

  override def tests: List[Test] =
    List(
      property(
        "sbt's terminal runs the thunk between entering and exiting raw mode",
        outcomeGen.forAll.map { outcome =>
          val events = ListBuffer.empty[String]
          val term = internalTerminal(events)
          try {
            val result = Try(Terminal(term).withRawInput(runThunk(outcome, events)))
            hedgehog.Result.all(
              List(
                events.toList ==== List("enterRawMode", "thunk", "exitRawMode"),
                checkResult(outcome, result)
              )
            )
          } finally term.close()
        }
      ),
      property(
        "the default implementation runs the thunk between two setMode calls",
        outcomeGen.forAll.map { outcome =>
          val events = ListBuffer.empty[String]
          val result = Try(customTerminal(events).withRawInput(runThunk(outcome, events)))
          hedgehog.Result.all(
            List(
              events.toList ==== List(
                "setMode(canonical = false, echo = false)",
                "thunk",
                "setMode(canonical = true, echo = true)"
              ),
              checkResult(outcome, result)
            )
          )
        }
      )
    )

  private val outcomeGen: Gen[Option[Int]] =
    Gen.choice1(Gen.constant(None), Gen.int(Range.linear(-100, 100)).map(Some(_)))

  private def runThunk(outcome: Option[Int], events: ListBuffer[String]): Int = {
    events += "thunk"
    outcome.getOrElse(throw new ThunkFailure)
  }

  private def checkResult(outcome: Option[Int], result: Try[Int]): hedgehog.Result =
    (outcome, result) match {
      case (Some(expected), Success(actual)) => actual ==== expected
      case (None, Failure(_: ThunkFailure))  => hedgehog.Result.success
      case (expected, actual)                => hedgehog.Result.failure.log(s"expected $expected but got $actual")
    }

  private def customTerminal(events: ListBuffer[String]): Terminal = new Terminal {
    override def getWidth: Int = 80
    override def getHeight: Int = 24
    override def inputStream: InputStream = () => -1
    override def printStream: PrintStream = new PrintStream((_ => ()): OutputStream)
    override def setMode(canonical: Boolean, echo: Boolean): Unit =
      events += s"setMode(canonical = $canonical, echo = $echo)"
  }

  private def internalTerminal(events: ListBuffer[String]): ITerminal =
    new ITerminal.TerminalImpl(
      new ITerminal.WriteableInputStream(() => -1, "test"),
      new ByteArrayOutputStream(),
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
      override private[sbt] def enterRawMode(): Unit = events += "enterRawMode"
      override private[sbt] def exitRawMode(): Unit = events += "exitRawMode"
    }
}
