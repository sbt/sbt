/* sbt -- Simple Build Tool
 * Copyright 2009, 2010 Mark Harrah
 */
package sbt

import java.io.{File,IOException}
import CacheIO.{fromFile, toFile}
import sbinary.{Format, JavaIO}
import scala.reflect.Manifest
import IO.{delete, read, write}


object Tracked
{
	/** Creates a tracker that provides the last time it was evaluated.
	* If 'useStartTime' is true, the recorded time is the start of the evaluated function.
	* If 'useStartTime' is false, the recorded time is when the evaluated function completes.
	* In both cases, the timestamp is not updated if the function throws an exception.*/
	def tstamp(cacheFile: File, useStartTime: Boolean = true): Timestamp = new Timestamp(cacheFile, useStartTime)
	/** Creates a tracker that only evaluates a function when the input has changed.*/
	def changed[O](cacheFile: File)(implicit format: Format[O], equiv: Equiv[O]): Changed[O] =
		new Changed[O](cacheFile)
	
	/** Creates a tracker that provides the difference between a set of input files for successive invocations.*/
	def diffInputs(cache: File, style: FilesInfo.Style): Difference =
		Difference.inputs(cache, style)
	/** Creates a tracker that provides the difference between a set of output files for successive invocations.*/
	def diffOutputs(cache: File, style: FilesInfo.Style): Difference =
		Difference.outputs(cache, style)
}

trait Tracked extends NotNull
{
	/** Cleans outputs and clears the cache.*/
	def clean: Unit
}
class Timestamp(val cacheFile: File, useStartTime: Boolean) extends Tracked
{
	def clean = delete(cacheFile)
	/** Reads the previous timestamp, evaluates the provided function,
	* and then updates the timestamp if the function completes normally.*/
	def apply[T](f: Long => T): T =
	{
		val start = now()
		val result = f(readTimestamp)
		write(cacheFile, (if(useStartTime) start else now()).toString)
		result
	}
	private def now() = System.currentTimeMillis
	def readTimestamp: Long =
		try { read(cacheFile).toLong }
		catch { case _: NumberFormatException | _: java.io.FileNotFoundException => 0 }
}

class Changed[O](val cacheFile: File)(implicit equiv: Equiv[O], format: Format[O]) extends Tracked
{
	def clean = delete(cacheFile)
	def apply[O2](ifChanged: O => O2, ifUnchanged: O => O2): O => O2 = value =>
	{
		if(uptodate(value))
			ifUnchanged(value)
		else
		{
			update(value)
			ifChanged(value)
		}
	}
	import JavaIO._
	def update(value: O): Unit = Using.fileOutputStream(false)(cacheFile)(stream => format.writes(stream, value))
	def uptodate(value: O): Boolean =
		try {
			Using.fileInputStream(cacheFile) {
				stream => equiv.equiv(value, format.reads(stream))
			}
		} catch {
			case _: IOException => false
		}
}
object Difference
{
	sealed class Constructor private[Difference](defineClean: Boolean, filesAreOutputs: Boolean) extends NotNull
	{
		def apply(cache: File, style: FilesInfo.Style): Difference = new Difference(cache, style, defineClean, filesAreOutputs)
	}
	/** Provides a constructor for a Difference that removes the files from the previous run on a call to 'clean' and saves the
	* hash/last modified time of the files as they are after running the function.  This means that this information must be evaluated twice:
	* before and after running the function.*/
	object outputs extends Constructor(true, true)
	/** Provides a constructor for a Difference that does nothing on a call to 'clean' and saves the
	* hash/last modified time of the files as they were prior to running the function.*/
	object inputs extends Constructor(false, false)
}
class Difference(val cache: File, val style: FilesInfo.Style, val defineClean: Boolean, val filesAreOutputs: Boolean) extends Tracked
{
	def clean =
	{
		if(defineClean) delete(raw(cachedFilesInfo)) else ()
		clearCache()
	}
	private def clearCache() = delete(cache)
	
	private def cachedFilesInfo = fromFile(style.formats, style.empty)(cache)(style.manifest).files
	private def raw(fs: Set[style.F]): Set[File] =  fs.map(_.file)
	
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

		val report = new ChangeReport[File]
		{
			lazy val checked = currentFiles
			lazy val removed = lastFiles -- checked // all files that were included previously but not this time.  This is independent of whether the files exist.
			lazy val added = checked -- lastFiles // all files included now but not previously.  This is independent of whether the files exist.
			lazy val modified = raw(lastFilesInfo -- currentFilesInfo.files) ++ added
			lazy val unmodified = checked -- modified
		}

		val result = f(report)
		val info = if(filesAreOutputs) style(abs(extractFiles(result))) else currentFilesInfo
		toFile(style.formats)(info)(cache)(style.manifest)
		result
	}
}

object FileFunction {
	type UpdateFunction = (ChangeReport[File], ChangeReport[File]) => Set[File]
	
	def cached(cacheBaseDirectory: File, inStyle: FilesInfo.Style = FilesInfo.lastModified, outStyle: FilesInfo.Style = FilesInfo.exists)(action: Set[File] => Set[File]): Set[File] => Set[File] =
		cached(cacheBaseDirectory)(inStyle, outStyle)( (in, out) => action(in.checked) )
	
	def cached(cacheBaseDirectory: File)(inStyle: FilesInfo.Style, outStyle: FilesInfo.Style)(action: UpdateFunction): Set[File] => Set[File] =
	{
		import Path._
		lazy val inCache = Difference.inputs(cacheBaseDirectory / "in-cache", inStyle)
		lazy val outCache = Difference.outputs(cacheBaseDirectory / "out-cache", outStyle)
		inputs =>
		{
			inCache(inputs) { inReport =>
				outCache { outReport =>
					if(inReport.modified.isEmpty && outReport.modified.isEmpty)
						outReport.checked
					else
						action(inReport, outReport)
				}
			}
		}
	}
}