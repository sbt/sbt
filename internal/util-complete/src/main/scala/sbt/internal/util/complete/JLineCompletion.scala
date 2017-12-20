/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util
package complete

import jline.console.ConsoleReader
import jline.console.completer.{ Completer, CompletionHandler }
import scala.annotation.tailrec
import scala.collection.JavaConverters

object JLineCompletion {
  def installCustomCompletor(reader: ConsoleReader, parser: Parser[_]): Unit =
    installCustomCompletor(reader)(parserAsCompletor(parser))

  def installCustomCompletor(reader: ConsoleReader)(
      complete: (String, Int) => (Seq[String], Seq[String])
  ): Unit =
    installCustomCompletor(customCompletor(complete), reader)

  def installCustomCompletor(
      complete: (ConsoleReader, Int) => Boolean,
      reader: ConsoleReader
  ): Unit = {
    reader.removeCompleter(DummyCompletor)
    reader.addCompleter(DummyCompletor)
    reader.setCompletionHandler(new CustomHandler(complete))
  }

  private[this] final class CustomHandler(completeImpl: (ConsoleReader, Int) => Boolean)
      extends CompletionHandler {
    private[this] var previous: Option[(String, Int)] = None
    private[this] var level: Int = 1

    override def complete(
        reader: ConsoleReader,
        candidates: java.util.List[CharSequence],
        position: Int
    ) = {
      val current = Some(bufferSnapshot(reader))
      level = if (current == previous) level + 1 else 1
      previous = current
      try completeImpl(reader, level)
      catch {
        case e: Exception =>
          reader.print("\nException occurred while determining completions.")
          e.printStackTrace()
          false
      }
    }
  }

  // always provides dummy completions so that the custom completion handler gets called
  //   (ConsoleReader doesn't call the handler if there aren't any completions)
  //   the custom handler will then throw away the candidates and call the custom function
  private[this] final object DummyCompletor extends Completer {
    override def complete(
        buffer: String,
        cursor: Int,
        candidates: java.util.List[CharSequence]
    ): Int = {
      candidates.asInstanceOf[java.util.List[String]] add "dummy"
      0
    }
  }

  def parserAsCompletor(p: Parser[_]): (String, Int) => (Seq[String], Seq[String]) =
    (str, level) => convertCompletions(Parser.completions(p, str, level))

  def convertCompletions(c: Completions): (Seq[String], Seq[String]) = {
    val cs = c.get
    if (cs.isEmpty)
      (Nil, "{invalid input}" :: Nil)
    else
      convertCompletions(cs)
  }

  def convertCompletions(cs: Set[Completion]): (Seq[String], Seq[String]) = {
    val (insert, display) =
      ((Set.empty[String], Set.empty[String]) /: cs) {
        case (t @ (insert, display), comp) =>
          if (comp.isEmpty) t else (insert + comp.append, appendNonEmpty(display, comp.display))
      }
    (insert.toSeq, display.toSeq.sorted)
  }

  def appendNonEmpty(set: Set[String], add: String) = if (add.trim.isEmpty) set else set + add

  def customCompletor(
      f: (String, Int) => (Seq[String], Seq[String])): (ConsoleReader, Int) => Boolean =
    (reader, level) => {
      val success = complete(beforeCursor(reader), reader => f(reader, level), reader)
      reader.flush()
      success
    }

  def bufferSnapshot(reader: ConsoleReader): (String, Int) = {
    val b = reader.getCursorBuffer
    (b.buffer.toString, b.cursor)
  }

  def beforeCursor(reader: ConsoleReader): String = {
    val b = reader.getCursorBuffer
    b.buffer.substring(0, b.cursor)
  }

  // returns false if there was nothing to insert and nothing to display
  def complete(
      beforeCursor: String,
      completions: String => (Seq[String], Seq[String]),
      reader: ConsoleReader
  ): Boolean = {
    val (insert, display) = completions(beforeCursor)
    val common = commonPrefix(insert)
    if (common.isEmpty)
      if (display.isEmpty)
        ()
      else
        showCompletions(display, reader)
    else
      appendCompletion(common, reader)

    !(common.isEmpty && display.isEmpty)
  }

  def appendCompletion(common: String, reader: ConsoleReader): Unit = {
    reader.getCursorBuffer.write(common)
    reader.redrawLine()
  }

  /**
   * `display` is assumed to be the exact strings requested to be displayed.
   * In particular, duplicates should have been removed already.
   */
  def showCompletions(display: Seq[String], reader: ConsoleReader): Unit = {
    printCompletions(display, reader)
    reader.drawLine()
  }

  def printCompletions(cs: Seq[String], reader: ConsoleReader): Unit = {
    val print = shouldPrint(cs, reader)
    reader.println()
    if (print) printLinesAndColumns(cs, reader)
  }

  def printLinesAndColumns(cs: Seq[String], reader: ConsoleReader): Unit = {
    val (lines, columns) = cs partition hasNewline
    for (line <- lines) {
      reader.print(line)
      if (line.charAt(line.length - 1) != '\n')
        reader.println()
    }
    reader.printColumns(JavaConverters.seqAsJavaList(columns.map(_.trim)))
  }

  def hasNewline(s: String): Boolean = s.indexOf('\n') >= 0

  def shouldPrint(cs: Seq[String], reader: ConsoleReader): Boolean = {
    val size = cs.size
    (size <= reader.getAutoprintThreshold) ||
    confirm("Display all %d possibilities? (y or n) ".format(size), 'y', 'n', reader)
  }

  def confirm(prompt: String, trueC: Char, falseC: Char, reader: ConsoleReader): Boolean = {
    reader.println()
    reader.print(prompt)
    reader.flush()
    reader.readCharacter(trueC, falseC) == trueC
  }

  def commonPrefix(s: Seq[String]): String = if (s.isEmpty) "" else s reduceLeft commonPrefix

  def commonPrefix(a: String, b: String): String = {
    val len = scala.math.min(a.length, b.length)
    @tailrec def loop(i: Int): Int = if (i >= len) len else if (a(i) != b(i)) i else loop(i + 1)
    a.substring(0, loop(0))
  }
}
