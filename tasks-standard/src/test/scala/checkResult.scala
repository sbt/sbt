/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package sbt

import scala.util.control.NonFatal

import org.scalacheck.Prop._

object checkResult {
  def apply[T](run: => T, expected: T) =
    {
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
