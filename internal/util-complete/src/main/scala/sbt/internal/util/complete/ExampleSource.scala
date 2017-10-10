/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util
package complete

import java.io.File
import sbt.io.IO

/**
 * These sources of examples are used in parsers for user input completion. An example of such a source is the
 * [[sbt.complete.FileExamples]] class, which provides a list of suggested files to the user as they press the
 * TAB key in the console.
 */
trait ExampleSource {

  /**
   * @return a (possibly lazy) list of completion example strings. These strings are continuations of user's input. The
   *         user's input is incremented with calls to [[withAddedPrefix]].
   */
  def apply(): Iterable[String]

  /**
   * @param addedPrefix a string that just typed in by the user.
   * @return a new source of only those examples that start with the string typed by the user so far (with addition of
   *         the just added prefix).
   */
  def withAddedPrefix(addedPrefix: String): ExampleSource

}

/**
 * A convenience example source that wraps any collection of strings into a source of examples.
 * @param examples the examples that will be displayed to the user when they press the TAB key.
 */
sealed case class FixedSetExamples(examples: Iterable[String]) extends ExampleSource {
  override def withAddedPrefix(addedPrefix: String): ExampleSource =
    FixedSetExamples(examplesWithRemovedPrefix(addedPrefix))

  override def apply(): Iterable[String] = examples

  private def examplesWithRemovedPrefix(prefix: String) = examples.collect {
    case example if example startsWith prefix => example substring prefix.length
  }
}

/**
 * Provides path completion examples based on files in the base directory.
 * @param base the directory within which this class will search for completion examples.
 * @param prefix the part of the path already written by the user.
 */
class FileExamples(base: File, prefix: String = "") extends ExampleSource {
  override def apply(): Stream[String] = files(base).map(_ substring prefix.length)

  override def withAddedPrefix(addedPrefix: String): FileExamples =
    new FileExamples(base, prefix + addedPrefix)

  protected def files(directory: File): Stream[String] = {
    val childPaths = IO.listFiles(directory).toStream
    val prefixedDirectChildPaths = childPaths map { IO.relativize(base, _).get } filter {
      _ startsWith prefix
    }
    val dirsToRecurseInto = childPaths filter { _.isDirectory } map { IO.relativize(base, _).get } filter {
      dirStartsWithPrefix
    }
    prefixedDirectChildPaths append dirsToRecurseInto.flatMap(dir => files(new File(base, dir)))
  }

  private def dirStartsWithPrefix(relativizedPath: String): Boolean =
    (relativizedPath startsWith prefix) || (prefix startsWith relativizedPath)
}
