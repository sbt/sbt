/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import scala.util.control.NonFatal

import org.scalacheck.Prop.*

object checkResult {
  def apply[T](run: => T, expected: T) = {
    ("Expected: " + expected) |:
      (try {
        val actual = run
        ("Actual: " + actual) |: (actual == expected)
      } catch {
        case i: Incomplete =>
          println(i)
          "One or more tasks failed" |: false
        case NonFatal(e) =>
          e.printStackTrace()
          "Error in framework" |: false
      })
  }
}
