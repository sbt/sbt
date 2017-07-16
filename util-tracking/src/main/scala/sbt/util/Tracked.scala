/* sbt -- Simple Build Tool
 * Copyright 2009, 2010 Mark Harrah
 */
package sbt.util

import scala.util.{ Failure, Try, Success }

import java.io.File
import sbt.io.IO
import sbt.io.syntax._

import sjsonnew.JsonFormat
import sjsonnew.support.murmurhash.Hasher

object Tracked {
  /**
   * Creates a tracker that provides the last time it was evaluated.
   * If the function throws an exception.
   */
  def tstamp(store: CacheStore): Timestamp = tstamp(store, true)

  /**
   * Creates a tracker that provides the last time it was evaluated.
   * If the function throws an exception.
   */
  def tstamp(cacheFile: File): Timestamp = tstamp(CacheStore(cacheFile))

  /**
   * Creates a tracker that provides the last time it was evaluated.
   * If 'useStartTime' is true, the recorded time is the start of the evaluated function.
   * If 'useStartTime' is false, the recorded time is when the evaluated function completes.
   * In both cases, the timestamp is not updated if the function throws an exception.
   */
  def tstamp(store: CacheStore, useStartTime: Boolean): Timestamp = {
    import CacheImplicits.LongJsonFormat
    new Timestamp(store, useStartTime)
  }

  /**
   * Creates a tracker that provides the last time it was evaluated.
   * If 'useStartTime' is true, the recorded time is the start of the evaluated function.
   * If 'useStartTime' is false, the recorded time is when the evaluated function completes.
   * In both cases, the timestamp is not updated if the function throws an exception.
   */
  def tstamp(cacheFile: File, useStartTime: Boolean): Timestamp = tstamp(CacheStore(cacheFile), useStartTime)

  /** Creates a tracker that provides the difference between a set of input files for successive invocations.*/
  def diffInputs(store: CacheStore, style: FileInfo.Style): Difference =
    Difference.inputs(store, style)

  /** Creates a tracker that provides the difference between a set of input files for successive invocations.*/
  def diffInputs(cacheFile: File, style: FileInfo.Style): Difference = diffInputs(CacheStore(cacheFile), style)

  /** Creates a tracker that provides the difference between a set of output files for successive invocations.*/
  def diffOutputs(store: CacheStore, style: FileInfo.Style): Difference =
    Difference.outputs(store, style)

  /** Creates a tracker that provides the difference between a set of output files for successive invocations.*/
  def diffOutputs(cacheFile: File, style: FileInfo.Style): Difference = diffOutputs(CacheStore(cacheFile), style)

  /** Creates a tracker that provides the output of the most recent invocation of the function */
  def lastOutput[I, O: JsonFormat](store: CacheStore)(f: (I, Option[O]) => O): I => O = { in =>
    val previous = Try { store.read[O] }.toOption
    val next = f(in, previous)
    store.write(next)
    next
  }

  /** Creates a tracker that provides the output of the most recent invocation of the function */
  def lastOutput[I, O: JsonFormat](cacheFile: File)(f: (I, Option[O]) => O): I => O =
    lastOutput(CacheStore(cacheFile))(f)

  /**
   * Creates a tracker that indicates whether the arguments given to f have changed since the most
   * recent invocation.
   */
  def inputChanged[I: JsonFormat: SingletonCache, O](store: CacheStore)(f: (Boolean, I) => O): I => O = { in =>
    val cache: SingletonCache[Long] = {
      import CacheImplicits.LongJsonFormat
      implicitly
    }
    val help = new CacheHelp(cache)
    val changed = help.changed(store, in)
    val result = f(changed, in)
    if (changed)
      help.save(store, in)
    result
  }

  /**
   * Creates a tracker that indicates whether the arguments given to f have changed since the most
   * recent invocation.
   */
  def inputChanged[I: JsonFormat: SingletonCache, O](cacheFile: File)(f: (Boolean, I) => O): I => O =
    inputChanged(CacheStore(cacheFile))(f)

  private final class CacheHelp[I: JsonFormat](val sc: SingletonCache[Long]) {
    import CacheImplicits.implicitHashWriter
    import CacheImplicits.LongJsonFormat
    def save(store: CacheStore, value: I): Unit = {
      Hasher.hash(value) match {
        case Success(keyHash) => store.write[Long](keyHash.toLong)
        case Failure(e)       => ()
      }
    }

    def changed(store: CacheStore, value: I): Boolean =
      Try { store.read[Long] } match {
        case Success(prev: Long) =>
          Hasher.hash(value) match {
            case Success(keyHash: Int) => keyHash.toLong != prev
            case Failure(_)            => true
          }
        case Failure(_) => true
      }
  }

}

trait Tracked {
  /** Cleans outputs and clears the cache.*/
  def clean(): Unit
}
class Timestamp(val store: CacheStore, useStartTime: Boolean)(implicit format: JsonFormat[Long]) extends Tracked {
  def clean() = store.delete()
  /**
   * Reads the previous timestamp, evaluates the provided function,
   * and then updates the timestamp if the function completes normally.
   */
  def apply[T](f: Long => T): T =
    {
      val start = now()
      val result = f(readTimestamp)
      store.write(if (useStartTime) start else now())
      result
    }
  private def now() = System.currentTimeMillis
  def readTimestamp: Long =
    Try { store.read[Long] } getOrElse 0
}

class Changed[O: Equiv: JsonFormat](val store: CacheStore) extends Tracked {
  def clean() = store.delete()
  def apply[O2](ifChanged: O => O2, ifUnchanged: O => O2): O => O2 = value =>
    {
      if (uptodate(value))
        ifUnchanged(value)
      else {
        update(value)
        ifChanged(value)
      }
    }

  def update(value: O): Unit = store.write(value) //Using.fileOutputStream(false)(cacheFile)(stream => format.writes(stream, value))
  def uptodate(value: O): Boolean = {
    val equiv: Equiv[O] = implicitly
    equiv.equiv(value, store.read[O])
  }
}
object Difference {
  def constructor(defineClean: Boolean, filesAreOutputs: Boolean): (CacheStore, FileInfo.Style) => Difference =
    (store, style) => new Difference(store, style, defineClean, filesAreOutputs)

  /**
   * Provides a constructor for a Difference that removes the files from the previous run on a call to 'clean' and saves the
   * hash/last modified time of the files as they are after running the function.  This means that this information must be evaluated twice:
   * before and after running the function.
   */
  val outputs = constructor(true, true)
  /**
   * Provides a constructor for a Difference that does nothing on a call to 'clean' and saves the
   * hash/last modified time of the files as they were prior to running the function.
   */
  val inputs = constructor(false, false)
}
class Difference(val store: CacheStore, val style: FileInfo.Style, val defineClean: Boolean, val filesAreOutputs: Boolean) extends Tracked {
  def clean() =
    {
      if (defineClean) IO.delete(raw(cachedFilesInfo)) else ()
      clearCache()
    }
  private def clearCache() = store.delete()

  private def cachedFilesInfo = store.read(default = FilesInfo.empty[style.F])(style.formats).files
  private def raw(fs: Set[style.F]): Set[File] = fs.map(_.file)

  def apply[T](files: Set[File])(f: ChangeReport[File] => T): T =
    {
      val lastFilesInfo = cachedFilesInfo
      apply(files, lastFilesInfo)(f)(_ => files)
    }

  def apply[T](f: ChangeReport[File] => T)(implicit toFiles: T => Set[File]): T =
    {
      val lastFilesInfo = cachedFilesInfo
      apply(raw(lastFilesInfo), lastFilesInfo)(f)(toFiles)
    }

  private def abs(files: Set[File]) = files.map(_.getAbsoluteFile)
  private[this] def apply[T](files: Set[File], lastFilesInfo: Set[style.F])(f: ChangeReport[File] => T)(extractFiles: T => Set[File]): T =
    {
      val lastFiles = raw(lastFilesInfo)
      val currentFiles = abs(files)
      val currentFilesInfo = style(currentFiles)

      val report = new ChangeReport[File] {
        lazy val checked = currentFiles
        lazy val removed = lastFiles -- checked // all files that were included previously but not this time.  This is independent of whether the files exist.
        lazy val added = checked -- lastFiles // all files included now but not previously.  This is independent of whether the files exist.
        lazy val modified = raw(lastFilesInfo -- currentFilesInfo.files) ++ added
        lazy val unmodified = checked -- modified
      }

      val result = f(report)
      val info = if (filesAreOutputs) style(abs(extractFiles(result))) else currentFilesInfo

      store.write(info)(style.formats)

      result
    }
}
