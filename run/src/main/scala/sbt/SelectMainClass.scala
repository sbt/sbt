/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.internal.util.ConsoleAppender
import sbt.internal.util.Util.{ AnyOps, none }

object SelectMainClass {
  // Some(SimpleReader.readLine _)
  def apply(
      promptIfMultipleChoices: Option[String => Option[String]],
      mainClasses: Seq[String]
  ): Option[String] = {
    mainClasses.toList match {
      case Nil         => None
      case head :: Nil => Some(head)
      case multiple =>
        promptIfMultipleChoices.flatMap { prompt =>
          val header = "\nMultiple main classes detected. Select one to run:\n"
          val classes = multiple.zipWithIndex
            .map { case (className, index) => s" [${index + 1}] $className" }
            .mkString("\n")
          println(ConsoleAppender.clearScreen(0) + header + classes)

          val line = trim(prompt("\nEnter number: "))
          toInt(line, multiple.length) map multiple.apply
        }
    }
  }
  private def trim(s: Option[String]) = s.getOrElse("")
  private def toInt(s: String, size: Int): Option[Int] =
    try {
      val i = s.toInt
      if (i > 0 && i <= size)
        (i - 1).some
      else {
        println("Number out of range: was " + i + ", expected number between 1 and " + size)
        none
      }
    } catch {
      case nfe: NumberFormatException =>
        println("Invalid number: " + nfe.toString)
        none
    }
}
