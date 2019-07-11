package sbt
package input.parser

import complete.Parser
import complete.Parser._

import java.io.{ PipedInputStream, PipedOutputStream }
import Keys._
import sbt.nio.Keys._

object Build {
  val root = (project in file(".")).settings(
    useSuperShell := false,
    watchInputStream := inputStream,
    watchStartMessage := { (_, _, _) =>
      Build.outputStream.write('\n'.toByte)
      Build.outputStream.flush()
      Some("default start message")
    }
  )
  val outputStream = new PipedOutputStream()
  val inputStream = new PipedInputStream(outputStream)
  val byeParser: Parser[Watch.Action] = "bye" ^^^ Watch.CancelWatch
  val helloParser: Parser[Watch.Action] = "hello" ^^^ Watch.Ignore
  // Note that the order is byeParser | helloParser. In general, we want the higher priority
  // action to come first because otherwise we would potentially scan past it.
  val helloOrByeParser: Parser[Watch.Action] = byeParser | helloParser
  val alternativeStartMessage: (Int, ProjectRef, Seq[String]) => Option[String] = { (_, _, _) =>
    outputStream.write("xybyexyblahxyhelloxy".getBytes)
    outputStream.flush()
    Some("alternative start message")
  }
  val otherAlternativeStartMessage: (Int, ProjectRef, Seq[String]) => Option[String] = { (_, _, _) =>
    outputStream.write("xyhellobyexyblahx".getBytes)
    outputStream.flush()
    Some("other alternative start message")
  }
}
