package xsbt

import java.io.File
import CacheIO.{fromFile, toFile}
import sbinary.Format
import scala.reflect.Manifest

trait Tracked extends NotNull
{
	def clear: Task[Unit]
	def clean: Task[Unit]
}
object Clean
{
	def apply(src: Task[Set[File]]): Task[Unit] = src map FileUtilities.delete
	def apply(srcs: File*): Task[Unit] = Task(FileUtilities.delete(srcs))
	def apply(srcs: Set[File]): Task[Unit] = Task(FileUtilities.delete(srcs))
}

class Changed[O](val task: Task[O], val file: File)(implicit input: InputCache[O]) extends Tracked
{
	def clean = Task.empty
	def clear = Clean(file)
	def apply[O2](ifChanged: O => O2, ifUnchanged: O => O2): Task[O2] { type Input = O } =
		task map { value =>
			val cache = OpenResource.fileInputStream(file)(input.uptodate(value))
			if(cache.uptodate)
				ifUnchanged(value)
			else
			{
				OpenResource.fileOutputStream(false)(file)(cache.update)
				ifChanged(value)
			}
		}
}
class Difference(val filesTask: Task[Set[File]], val style: FilesInfo.Style, val cache: File, val shouldClean: Boolean) extends Tracked
{
	def this(filesTask: Task[Set[File]], style: FilesInfo.Style, cache: File) = this(filesTask, style, cache, false)
	def this(files: Set[File], style: FilesInfo.Style, cache: File, shouldClean: Boolean) = this(Task(files), style, cache)
	def this(files: Set[File], style: FilesInfo.Style, cache: File) = this(Task(files), style, cache, false)
	
	val clear = Clean(cache)
	val clean = if(shouldClean) cleanTask else Task.empty
	def cleanTask = Clean(Task(raw(cachedFilesInfo)))
	
	private def cachedFilesInfo = fromFile(style.formats)(cache)(style.manifest).files
	private def raw(fs: Set[style.F]): Set[File] = fs.map(_.file)
	
	def apply[T](f: ChangeReport[File] => Task[T]): Task[T] =
		filesTask bind { files =>
			val lastFilesInfo = cachedFilesInfo
			val lastFiles = raw(lastFilesInfo)
			val currentFiles = files.map(_.getAbsoluteFile)
			val currentFilesInfo = style(files)

			val report = new ChangeReport[File]
			{
				lazy val allInputs = currentFiles
				lazy val removed = lastFiles -- allInputs
				lazy val added = allInputs -- lastFiles
				lazy val modified = raw(lastFilesInfo -- currentFilesInfo.files)
				lazy val unmodified = allInputs -- modified
			}

			f(report) map { result =>
				toFile(style.formats)(currentFilesInfo)(cache)(style.manifest)
				result
			}
		}
}
object InvalidateFiles
{
	def apply(cacheDirectory: File): Invalidate[File] = apply(cacheDirectory, true)
	def apply(cacheDirectory: File, translateProducts: Boolean): Invalidate[File] =
	{
		import sbinary.DefaultProtocol.FileFormat
		new Invalidate[File](cacheDirectory, translateProducts, FileUtilities.delete)
	}
}
class Invalidate[T](val cacheDirectory: File, val translateProducts: Boolean, cleanT: T => Unit)
	(implicit format: Format[T], mf: Manifest[T]) extends Tracked
{
	def this(cacheDirectory: File, translateProducts: Boolean)(implicit format: Format[T], mf: Manifest[T]) =
		this(cacheDirectory, translateProducts, x => ())

	private val trackFormat = new TrackingFormat[T](cacheDirectory, translateProducts)
	private def cleanAll(fs: Set[T]) = fs.foreach(cleanT)

	def clear = Clean(cacheDirectory)
	def clean = Task(cleanAll(trackFormat.read.allProducts))
	def apply[R](changes: ChangeReport[T])(f: (InvalidationReport[T], UpdateTracking[T]) => Task[R]): Task[R] =
		apply(Task(changes))(f)
	def apply[R](changesTask: Task[ChangeReport[T]])(f: (InvalidationReport[T], UpdateTracking[T]) => Task[R]): Task[R] =
	{
		changesTask bind { changes =>
			val tracker = trackFormat.read
			def invalidatedBy(file: T) = tracker.products(file) ++ tracker.sources(file) ++ tracker.usedBy(file) ++ tracker.dependsOn(file)

			import scala.collection.mutable.HashSet
			val invalidated = new HashSet[T]
			val invalidatedProducts = new HashSet[T]
			def invalidate(files: Iterable[T]): Unit =
				for(file <- files if !invalidated(file))
				{
					invalidated += file
					if(!tracker.sources(file).isEmpty) invalidatedProducts += file
					invalidate(invalidatedBy(file))
				}

			invalidate(changes.modified)
			tracker.removeAll(invalidated)

			val report = new InvalidationReport[T]
			{
				val invalid = Set(invalidated.toSeq : _*)
				val invalidProducts = Set(invalidatedProducts.toSeq : _*)
				val valid = changes.unmodified -- invalid
			}
			cleanAll(report.invalidProducts)
			
			f(report, tracker) map { result =>
				trackFormat.write(tracker)
				result
			}
		}
	}
}
class BasicTracked(filesTask: Task[Set[File]], style: FilesInfo.Style, cacheDirectory: File) extends Tracked
{
	private val changed = new Difference(filesTask, style, new File(cacheDirectory, "files"))
	private val invalidation = InvalidateFiles(cacheDirectory)
	val clean = invalidation.clean
	val clear = Clean(cacheDirectory)
	
	def apply[R](f: (ChangeReport[File], InvalidationReport[File], UpdateTracking[File]) => Task[R]): Task[R] =
		changed { sourceChanges =>
			invalidation(sourceChanges) { (report, tracking) =>
				f(sourceChanges, report, tracking)
			}
		}
}