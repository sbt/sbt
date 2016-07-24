/* sbt -- Simple Build Tool
 * Copyright 2009, 2010 Mark Harrah
 */
package sbt.internal.util

import scala.util.{ Failure, Try, Success }

import java.io.File
import sbt.io.IO
import sbt.io.syntax._

import sjsonnew.JsonFormat

object Tracked {

  import CacheImplicits.LongJsonFormat

  /**
   * Creates a tracker that provides the last time it was evaluated.
   * If 'useStartTime' is true, the recorded time is the start of the evaluated function.
   * If 'useStartTime' is false, the recorded time is when the evaluated function completes.
   * In both cases, the timestamp is not updated if the function throws an exception.
   */
  def tstamp(store: CacheStore, useStartTime: Boolean = true): Timestamp = new Timestamp(store, useStartTime)

  /** Creates a tracker that provides the difference between a set of input files for successive invocations.*/
  def diffInputs(store: CacheStore, style: FileInfo.Style): Difference =
    Difference.inputs(store, style)

  /** Creates a tracker that provides the difference between a set of output files for successive invocations.*/
  def diffOutputs(store: CacheStore, style: FileInfo.Style): Difference =
    Difference.outputs(store, style)

  /** Creates a tracker that provides the output of the most recent invocation of the function */
  def lastOutput[I, O: JsonFormat](store: CacheStore)(f: (I, Option[O]) => O): I => O = { in =>
    val previous = Try { store.read[O] }.toOption
    val next = f(in, previous)
    store.write(next)
    next
  }

  /**
   * Creates a tracker that indicates whether the arguments given to f have changed since the most
   * recent invocation.
   */
  def inputChanged[I: JsonFormat: SingletonCache, O](store: CacheStore)(f: (Boolean, I) => O): I => O = { in =>
    val cache: SingletonCache[I] = implicitly
    val help = new CacheHelp(cache)
    val changed = help.changed(store, in)
    val result = f(changed, in)
    if (changed)
      help.save(store, in)
    result
  }

  private final class CacheHelp[I: JsonFormat](val sc: SingletonCache[I]) {
    def save(store: CacheStore, value: I): Unit = {
      store.write(value)
    }

    def changed(store: CacheStore, value: I): Boolean =
      Try { store.read[I] } match {
        case Success(prev) => !sc.equiv.equiv(value, prev)
        case Failure(_)    => true
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

  private def cachedFilesInfo = store.read(default = FilesInfo.empty[style.F]).files //(style.formats).files
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

      store.write(info)

      result
    }
}

object FileFunction {
  type UpdateFunction = (ChangeReport[File], ChangeReport[File]) => Set[File]

  /**
   * Generic change-detection helper used to help build / artifact generation /
   * etc. steps detect whether or not they need to run. Returns a function whose
   * input is a Set of input files, and subsequently executes the action function
   * (which does the actual work: compiles, generates resources, etc.), returning
   * a Set of output files that it generated.
   *
   * The input file and resulting output file state is cached in stores issued by
   * `storeFactory`. On each invocation, the state of the input and output
   * files from the previous run is compared against the cache, as is the set of
   * input files. If a change in file state / input files set is detected, the
   * action function is re-executed.
   *
   * @param storeFactory The factory to use to get stores for the input and output files.
   * @param inStyle The strategy by which to detect state change in the input files from the previous run
   * @param outStyle The strategy by which to detect state change in the output files from the previous run
   * @param action The work function, which receives a list of input files and returns a list of output files
   */
  def cached(storeFactory: CacheStoreFactory, inStyle: FileInfo.Style = FileInfo.lastModified, outStyle: FileInfo.Style = FileInfo.exists)(action: Set[File] => Set[File]): Set[File] => Set[File] =
    cached(storeFactory)(inStyle, outStyle)((in, out) => action(in.checked))

  def cached(storeFactory: CacheStoreFactory)(inStyle: FileInfo.Style, outStyle: FileInfo.Style)(action: UpdateFunction): Set[File] => Set[File] =
    {
      lazy val inCache = Difference.inputs(storeFactory.derive("in-cache"), inStyle)
      lazy val outCache = Difference.outputs(storeFactory.derive("out-cache"), outStyle)
      inputs =>
        {
          inCache(inputs) { inReport =>
            outCache { outReport =>
              if (inReport.modified.isEmpty && outReport.modified.isEmpty)
                outReport.checked
              else
                action(inReport, outReport)
            }
          }
        }
    }
}
