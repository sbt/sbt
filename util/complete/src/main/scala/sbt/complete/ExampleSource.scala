package sbt.complete

import java.io.File

/**
 * These sources of examples are used in parsers for user input completion. An example of such a source is the
 * [[sbt.complete.FileExamples]] class, which provides a list of suggested files to the user as they press the
 * TAB key in the console.
 */
trait ExampleSource
{
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
 * Provides path completion examples based on files in the base directory.
 * @param base the directory within which this class will search for completion examples.
 * @param prefix the part of the path already written by the user.
 */
class FileExamples(base: File, prefix: String = "") extends ExampleSource {
  private val relativizedPrefix: String = "." + File.separator + prefix

  override def apply(): Iterable[String] = files(base).map(_.toString.substring(relativizedPrefix.length))

  override def withAddedPrefix(addedPrefix: String): FileExamples = new FileExamples(base, prefix + addedPrefix)

  protected def fileStartsWithPrefix(path: File): Boolean = path.toString.startsWith(relativizedPrefix)

  protected def directoryStartsWithPrefix(path: File): Boolean = {
    val pathString = path.toString
    pathString.startsWith(relativizedPrefix) || relativizedPrefix.startsWith(pathString)
  }

  protected def files(directory: File): Iterable[File] = {
    val (subDirectories, filesOnly) = directory.listFiles().toStream.partition(_.isDirectory)
    filesOnly.filter(fileStartsWithPrefix) ++ subDirectories.filter(directoryStartsWithPrefix).flatMap(files)
  }
}