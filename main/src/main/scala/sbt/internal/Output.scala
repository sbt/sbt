/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.internal.util.Types
import sbt.util.Show
import java.util.regex.Pattern
import java.io.File
import Keys.Streams
import Def.ScopedKey
import Aggregation.{ KeyValue, Values }
import Types.idFun
import Highlight.{ bold, showMatches }
import annotation.tailrec

import sbt.io.IO

object Output {
  final val DefaultTail = "> "

  def last(
      keys: Values[Any],
      streams: Streams,
      printLines: Seq[String] => Unit,
      sid: Option[String]
  )(using display: Show[ScopedKey[?]]): Unit =
    printLines(flatLines(lastLines(keys, streams, sid))(idFun))

  def last(file: File, printLines: Seq[String] => Unit, tailDelim: String = DefaultTail): Unit =
    printLines(tailLines(file, tailDelim))

  def lastGrep(
      keys: Values[Any],
      streams: Streams,
      patternString: String,
      printLines: Seq[String] => Unit
  )(using display: Show[ScopedKey[?]]): Unit = {
    val pattern = Pattern.compile(patternString)
    val lines = flatLines(lastLines(keys, streams))(_ flatMap showMatches(pattern))
    printLines(lines)
  }

  def lastGrep(
      file: File,
      patternString: String,
      printLines: Seq[String] => Unit,
      tailDelim: String = DefaultTail
  ): Unit =
    printLines(grep(tailLines(file, tailDelim), patternString))

  def grep(lines: Seq[String], patternString: String): Seq[String] =
    lines.flatMap(showMatches(Pattern.compile(patternString)))

  def flatLines(outputs: Values[Seq[String]])(f: Seq[String] => Seq[String])(using
      display: Show[ScopedKey[?]]
  ): Seq[String] = {
    val single = outputs.size == 1
    outputs flatMap { case KeyValue(key, lines) =>
      val flines = f(lines)
      if (!single) bold(display.show(key)) +: flines else flines
    }
  }

  def lastLines(
      keys: Values[Any],
      streams: Streams,
      sid: Option[String] = None
  ): Values[Seq[String]] = {
    val outputs = keys map { (kv: KeyValue[?]) =>
      KeyValue(kv.key, lastLines(kv.key, streams, sid))
    }
    outputs.filterNot(_.value.isEmpty)
  }

  def lastLines(key: ScopedKey[?], mgr: Streams, sid: Option[String]): Seq[String] =
    mgr.use(key) { s =>
      // Workaround for #1155 - Keys.streams are always scoped by the task they're included in
      // but are keyed by the Keys.streams key.  I think this isn't actually a workaround, but
      // is how things are expected to work now.
      // You can see where streams are injected using their own key scope in
      // EvaluateTask.injectStreams.
      val streamScopedKey: ScopedKey[?] =
        ScopedKey(Project.fillTaskAxis(key).scope, Keys.streams.key)
      val tmp = s.readText(streamScopedKey, sid)
      IO.readLines(tmp)
    }

  def tailLines(file: File, tailDelim: String): Seq[String] =
    headLines(IO.readLines(file).reverse, tailDelim).reverse

  @tailrec def headLines(lines: Seq[String], tailDelim: String): Seq[String] =
    if (lines.isEmpty) lines
    else {
      val (first, tail) = lines.span { line =>
        !line.startsWith(tailDelim)
      }
      if (first.isEmpty) headLines(tail drop 1, tailDelim) else first
    }
}
