/* sbt -- Simple Build Tool
 * Copyright 2010 Tony Sloane
 */
package sbt.internal.util

import sbt.io.IO
import scala.collection.mutable.ListBuffer

object StackTrace {
  def isSbtClass(name: String) = name.startsWith("sbt.") || name.startsWith("xsbt.")

  /**
   * Return a printable representation of the stack trace associated
   * with t.  Information about t and its Throwable causes is included.
   * The number of lines to be included for each Throwable is configured
   * via d which should be greater than or equal to 0.
   *
   * - If d is 0, then all elements are included up to (but not including)
   *   the first element that comes from sbt.
   * - If d is greater than 0, then up to that many lines are included,
   *   where the line for the Throwable is counted plus one line for each stack element.
   *   Less lines will be included if there are not enough stack elements.
   *
   * See also ConsoleAppender where d <= 2 is treated specially by
   * printing a prepared statement.
   */
  def trimmedLines(t: Throwable, d: Int): List[String] = {
    require(d >= 0)
    val b = new ListBuffer[String]()

    def appendStackTrace(t: Throwable, first: Boolean): Unit = {

      val include: StackTraceElement => Boolean =
        if (d == 0)
          element => !isSbtClass(element.getClassName)
        else {
          var count = d - 1
          (_ => { count -= 1; count >= 0 })
        }

      def appendElement(e: StackTraceElement): Unit = {
        b.append("\tat " + e)
        ()
      }

      if (!first) b.append("Caused by: " + t.toString)
      else b.append(t.toString)

      val els = t.getStackTrace()
      var i = 0
      while ((i < els.size) && include(els(i))) {
        appendElement(els(i))
        i += 1
      }

    }

    appendStackTrace(t, true)
    var c = t
    while (c.getCause() != null) {
      c = c.getCause()
      appendStackTrace(c, false)
    }
    b.toList
  }

  /**
   * Return a printable representation of the stack trace associated
   * with t.  Information about t and its Throwable causes is included.
   * The number of lines to be included for each Throwable is configured
   * via d which should be greater than or equal to 0.
   *
   * - If d is 0, then all elements are included up to (but not including)
   *   the first element that comes from sbt.
   * - If d is greater than 0, then up to that many lines are included,
   *   where the line for the Throwable is counted plus one line for each stack element.
   *   Less lines will be included if there are not enough stack elements.
   */
  def trimmed(t: Throwable, d: Int): String =
    trimmedLines(t, d).mkString(IO.Newline)
}
