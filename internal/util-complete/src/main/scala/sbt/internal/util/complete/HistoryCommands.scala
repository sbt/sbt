/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util
package complete

import sbt.io.IO

object HistoryCommands {
  val Start = "!"
  // second characters
  val Contains = "?"
  val Last = "!"
  val ListCommands = ":"

  def ContainsFull = h(Contains)
  def LastFull = h(Last)
  def ListFull = h(ListCommands)

  def ListN = ListFull + "n"
  def ContainsString = ContainsFull + "string"
  def StartsWithString = Start + "string"
  def Previous = Start + "-n"
  def Nth = Start + "n"

  private def h(s: String) = Start + s
  def plainCommands = Seq(ListFull, Start, LastFull, ContainsFull)

  def descriptions = Seq(
    LastFull -> "Execute the last command again",
    ListFull -> "Show all previous commands",
    ListN -> "Show the last n commands",
    Nth -> ("Execute the command with index n, as shown by the " + ListFull + " command"),
    Previous -> "Execute the nth command before this one",
    StartsWithString -> "Execute the most recent command starting with 'string'",
    ContainsString -> "Execute the most recent command containing 'string'"
  )

  def helpString =
    "History commands:\n   " + (descriptions
      .map { case (c, d) => c + "    " + d })
      .mkString("\n   ")

  def printHelp(): Unit = println(helpString)

  def printHistory(history: complete.History, historySize: Int, show: Int): Unit =
    history.list(historySize, show).foreach(println)

  import DefaultParsers._

  val MaxLines = 500
  lazy val num = token(NatBasic, "<integer>")
  lazy val last = Last ^^^ { execute(_.!!) }

  lazy val list = ListCommands ~> (num ?? Int.MaxValue) map { show => (h: History) =>
    { printHistory(h, MaxLines, show); Some(Nil) }
  }

  lazy val execStr = flag('?') ~ token(any.+.string, "<string>") map {
    case (contains, str) =>
      execute(h => if (contains) h !? str else h ! str)
  }

  lazy val execInt = flag('-') ~ num map {
    case (neg, value) =>
      execute(h => if (neg) h !- value else h ! value)
  }

  lazy val help = success((h: History) => { printHelp(); Some(Nil) })

  def execute(f: History => Option[String]): History => Option[List[String]] = (h: History) => {
    val command = f(h).filterNot(_.startsWith(Start))
    val lines = h.lines.toArray
    command.foreach(lines(lines.length - 1) = _)
    h.path foreach { h =>
      IO.writeLines(h, lines)
    }
    Some(command.toList)
  }

  val actionParser: Parser[complete.History => Option[List[String]]] =
    Start ~> (help | last | execInt | list | execStr) // execStr must come last
}
