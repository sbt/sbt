package sbt
package input.parser

import complete.Parser
import complete.Parser._

import java.io.{ PipedInputStream, PipedOutputStream }

object Build {
  val outputStream = new PipedOutputStream()
  val inputStream = new PipedInputStream(outputStream)
  val byeParser: Parser[Watched.Action] = "bye" ^^^ Watched.CancelWatch
  val helloParser: Parser[Watched.Action] = "hello" ^^^ Watched.Ignore
  // Note that the order is byeParser | helloParser. In general, we want the higher priority
  // action to come first because otherwise we would potentially scan past it.
  val helloOrByeParser: Parser[Watched.Action] = byeParser | helloParser
  val alternativeStartMessage: Int => Option[String] = { _ =>
    outputStream.write("xybyexyblahxyhelloxy".getBytes)
    outputStream.flush()
    Some("alternative start message")
  }
  val otherAlternativeStartMessage: Int => Option[String] = { _ =>
    outputStream.write("xyhellobyexyblahx".getBytes)
    outputStream.flush()
    Some("other alternative start message")
  }
}