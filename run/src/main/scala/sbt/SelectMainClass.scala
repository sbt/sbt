/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

object SelectMainClass {
  // Some(SimpleReader.readLine _)
  def apply(promptIfMultipleChoices: Option[String => Option[String]],
            mainClasses: Seq[String]): Option[String] = {
    mainClasses.toList match {
      case Nil         => None
      case head :: Nil => Some(head)
      case multiple =>
        promptIfMultipleChoices flatMap { prompt =>
          println("\nMultiple main classes detected, select one to run:\n")
          for ((className, index) <- multiple.zipWithIndex)
            println(" [" + (index + 1) + "] " + className)
          val line = trim(prompt("\nEnter number: "))
          println("")
          toInt(line, multiple.length) map multiple.apply
        }
    }
  }
  private def trim(s: Option[String]) = s.getOrElse("")
  private def toInt(s: String, size: Int) =
    try {
      val i = s.toInt
      if (i > 0 && i <= size)
        Some(i - 1)
      else {
        println("Number out of range: was " + i + ", expected number between 1 and " + size)
        None
      }
    } catch {
      case nfe: NumberFormatException =>
        println("Invalid number: " + nfe.toString)
        None
    }
}
