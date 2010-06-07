/* sbt -- Simple Build Tool
 * Copyright 2009, 2010 Mark Harrah
 */
package xsbt

import java.io.{File,IOException}
import CacheIO.{fromFile, toFile}
import sbinary.Format
import scala.reflect.Manifest
import xsbt.FileUtilities.{delete, read, write}

/* A proper implementation of fileTask that tracks inputs and outputs properly

def fileTask(cacheBaseDirectory: Path)(inputs: PathFinder, outputs: PathFinder)(action: => Unit): Task =
	fileTask(cacheBaseDirectory, FilesInfo.hash, FilesInfo.lastModified)
def fileTask(cacheBaseDirectory: Path, inStyle: FilesInfo.Style, outStyle: FilesInfo.Style)(inputs: PathFinder, outputs: PathFinder)(action: => Unit): Task =
{
	lazy val inCache = diffInputs(base / "in-cache", inStyle)(inputs)
	lazy val outCache = diffOutputs(base / "out-cache", outStyle)(outputs)
	task
	{
		inCache { inReport =>
			outCache { outReport =>
				if(inReport.modified.isEmpty && outReport.modified.isEmpty) () else action
			}
		}
	}
}
*/

object Tracked
{
	/** Creates a tracker that provides the last time it was evaluated.
	* If 'useStartTime' is true, the recorded time is the start of the evaluated function.
	* If 'useStartTime' is false, the recorded time is when the evaluated function completes.
	* In both cases, the timestamp is not updated if the function throws an exception.*/
	def tstamp(cacheFile: File, useStartTime: Boolean): Timestamp = new Timestamp(cacheFile)
	/** Creates a tracker that only evaluates a function when the input has changed.*/
	def changed[O](cacheFile: File)(getValue: => O)(implicit input: InputCache[O]): Changed[O] =
		new Changed[O](getValue, cacheFile)
	
	/** Creates a tracker that provides the difference between the set of input files provided for successive invocations.*/
	def diffInputs(cache: File, style: FilesInfo.Style)(files: => Set[File]): Difference =
		Difference.inputs(files, style, cache)
	/** Creates a tracker that provides the difference between the set of output files provided for successive invocations.*/
	def diffOutputs(cache: File, style: FilesInfo.Style)(files: => Set[File]): Difference =
		Difference.outputs(files, style, cache)
}

trait Tracked extends NotNull
{
	/** Cleans outputs and clears the cache.*/
	def clean: Unit
}
class Timestamp(val cacheFile: File, useStartTime: Boolean) extends Tracked
{
	def clean = delete(cacheFile)
	/** Reads the previous timestamp, evaluates the provided function, and then updates the timestamp.*/
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

class Changed[O](getValue: => O, val cacheFile: File)(implicit input: InputCache[O]) extends Tracked
{
	def clean = delete(cacheFile)
	def apply[O2](ifChanged: O => O2, ifUnchanged: O => O2): O2 =
	{
		val value = getValue
		val cache =
			try { OpenResource.fileInputStream(cacheFile)(input.uptodate(value)) }
			catch { case _: IOException => new ForceResult(input)(value) }
		if(cache.uptodate)
			ifUnchanged(value)
		else
		{
			OpenResource.fileOutputStream(false)(cacheFile)(cache.update)
			ifChanged(value)
		}
	}
}
object Difference
{
	sealed class Constructor private[Difference](defineClean: Boolean, filesAreOutputs: Boolean) extends NotNull
	{
		def apply(files: => Set[File], style: FilesInfo.Style, cache: File): Difference = new Difference(files, style, cache, defineClean, filesAreOutputs)
	}
	/** Provides a constructor for a Difference that removes the files from the previous run on a call to 'clean' and saves the
	* hash/last modified time of the files as they are after running the function.  This means that this information must be evaluated twice:
	* before and after running the function.*/
	object outputs extends Constructor(true, true)
	/** Provides a constructor for a Difference that does nothing on a call to 'clean' and saves the
	* hash/last modified time of the files as they were prior to running the function.*/
	object inputs extends Constructor(false, false)
}
class Difference(getFiles: => Set[File], val style: FilesInfo.Style, val cache: File, val defineClean: Boolean, val filesAreOutputs: Boolean) extends Tracked
{
	def clean =
	{
		if(defineClean) delete(raw(cachedFilesInfo)) else ()
		clearCache()
	}
	private def clearCache = delete(cache)
	
	private def cachedFilesInfo = fromFile(style.formats, style.empty)(cache)(style.manifest).files
	private def raw(fs: Set[style.F]): Set[File] = fs.map(_.file)
	
	def apply[T](f: ChangeReport[File] => T): T =
	{
		val files = getFiles
		val lastFilesInfo = cachedFilesInfo
		val lastFiles = raw(lastFilesInfo)
		val currentFiles = files.map(_.getAbsoluteFile)
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
		val info = if(filesAreOutputs) style(currentFiles) else currentFilesInfo
		toFile(style.formats)(info)(cache)(style.manifest)
		result
	}
}
class DependencyTracked[T](val cacheDirectory: File, val translateProducts: Boolean, cleanT: T => Unit)(implicit format: Format[T], mf: Manifest[T]) extends Tracked
{
	private val trackFormat = new TrackingFormat[T](cacheDirectory, translateProducts)
	private def cleanAll(fs: Set[T]) = fs.foreach(cleanT)
	
	def clean =
	{
		cleanAll(trackFormat.read.allProducts)
		delete(cacheDirectory)
	}
	
	def apply[R](f: UpdateTracking[T] => R): R =
	{
		val tracker = trackFormat.read
		val result = f(tracker)
		trackFormat.write(tracker)
		result
	}
}
object InvalidateFiles
{
	def apply(cacheDirectory: File): InvalidateTransitive[File] = apply(cacheDirectory, true)
	def apply(cacheDirectory: File, translateProducts: Boolean): InvalidateTransitive[File] =
	{
		import sbinary.DefaultProtocol.FileFormat
		new InvalidateTransitive[File](cacheDirectory, translateProducts, FileUtilities.delete)
	}
}

object InvalidateTransitive
{
	import scala.collection.Set
	def apply[T](tracker: UpdateTracking[T], files: Set[T]): InvalidationReport[T] =
	{
		val readTracker = tracker.read
		val invalidated = Set() ++ invalidate(readTracker, files)
		val invalidatedProducts = Set() ++ invalidated.filter(readTracker.isProduct)

		new InvalidationReport[T]
		{
			val invalid = invalidated
			val invalidProducts = invalidatedProducts
			val valid = Set() ++ files -- invalid
		}
	}
	def andClean[T](tracker: UpdateTracking[T], cleanImpl: Set[T] => Unit, files: Set[T]): InvalidationReport[T] =
	{
		val report = apply(tracker, files)
		clean(tracker, cleanImpl, report)
		report
	}
	def clear[T](tracker: UpdateTracking[T], report: InvalidationReport[T]): Unit =
		tracker.removeAll(report.invalid)
	def clean[T](tracker: UpdateTracking[T], cleanImpl: Set[T] => Unit, report: InvalidationReport[T])
	{
		clear(tracker, report)
		cleanImpl(report.invalidProducts)
	}
	
	private def invalidate[T](tracker: ReadTracking[T], files: Iterable[T]): Set[T] =
	{
		import scala.collection.mutable.HashSet
		val invalidated = new HashSet[T]
		def invalidate0(files: Iterable[T]): Unit =
			for(file <- files if !invalidated(file))
			{
				invalidated += file
				invalidate0(invalidatedBy(tracker, file))
			}
		invalidate0(files)
		invalidated
	}
	private def invalidatedBy[T](tracker: ReadTracking[T], file: T) =
		tracker.products(file) ++ tracker.sources(file) ++ tracker.usedBy(file) ++ tracker.dependsOn(file)
		
}
class InvalidateTransitive[T](cacheDirectory: File, translateProducts: Boolean, cleanT: T => Unit)
	(implicit format: Format[T], mf: Manifest[T]) extends Tracked
{
	def this(cacheDirectory: File, translateProducts: Boolean)(implicit format: Format[T], mf: Manifest[T]) =
		this(cacheDirectory, translateProducts, (_: T) => ())
		
	private val tracked = new DependencyTracked(cacheDirectory, translateProducts, cleanT)
	def clean
	{
		tracked.clean
		tracked.clear
	}

	def apply[R](getChanges: => ChangeReport[T])(f: (InvalidationReport[T], UpdateTracking[T]) => R): R =
	{
		val changes = getChanges
		tracked { tracker =>
			val report = InvalidateTransitive.andClean[T](tracker, _.foreach(cleanT), changes.modified)
			f(report, tracker)
		}
	}
}
class BasicTracked(files: => Set[File], style: FilesInfo.Style, cacheDirectory: File) extends Tracked
{
	private val changed = Difference.inputs(files, style, new File(cacheDirectory, "files"))
	private val invalidation = InvalidateFiles(new File(cacheDirectory, "invalidation"))
	private def onTracked(f: Tracked => Unit) = { f(invalidation); f(changed) }
	def clean = onTracked(_.clean)
	
	def apply[R](f: (ChangeReport[File], InvalidationReport[File], UpdateTracking[File]) => R): R =
		changed { sourceChanges =>
			invalidation(sourceChanges) { (report, tracking) =>
				f(sourceChanges, report, tracking)
			}
		}
}