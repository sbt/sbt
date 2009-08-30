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
class Difference[F <: FileInfo](val filesTask: Task[Set[File]], val style: FilesInfo.Style[F], val cache: File, val shouldClean: Boolean)(implicit mf: Manifest[F]) extends Tracked
{
	def this(filesTask: Task[Set[File]], style: FilesInfo.Style[F], cache: File)(implicit mf: Manifest[F]) = this(filesTask, style, cache, false)
	def this(files: Set[File], style: FilesInfo.Style[F], cache: File, shouldClean: Boolean)(implicit mf: Manifest[F]) = this(Task(files), style, cache)
	def this(files: Set[File], style: FilesInfo.Style[F], cache: File)(implicit mf: Manifest[F]) = this(Task(files), style, cache, false)
	
	val clear = Clean(cache)
	val clean = if(shouldClean) cleanTask else Task.empty
	def cleanTask = Clean(Task(raw(cachedFilesInfo)))
	
	private def cachedFilesInfo = fromFile(style.formats)(cache).files
	private def raw(fs: Set[F]): Set[File] = fs.map(_.file)
	
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
				toFile(style.formats)(currentFilesInfo)(cache)
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
class BasicTracked[F <: FileInfo](filesTask: Task[Set[File]], style: FilesInfo.Style[F], cacheDirectory: File)(implicit mf: Manifest[F]) extends Tracked
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
private object DependencyTracking
{
	import scala.collection.mutable.{Set, HashMap, Map, MultiMap}
	type DependencyMap[T] = HashMap[T, Set[T]] with MultiMap[T, T]
	def newMap[T]: DependencyMap[T] = new HashMap[T, Set[T]] with MultiMap[T, T]
	type TagMap[T] = Map[T, Array[Byte]]
	def newTagMap[T] = new HashMap[T, Array[Byte]]
}

trait UpdateTracking[T] extends NotNull
{
	def dependency(source: T, dependsOn: T): Unit
	def use(source: T, uses: T): Unit
	def product(source: T, output: T): Unit
	def tag(source: T, t: Array[Byte]): Unit
	def read: ReadTracking[T]
}
object Clean
{
	def apply(src: Task[Set[File]]): Task[Unit] = src map FileUtilities.delete
	def apply(srcs: File*): Task[Unit] = Task(FileUtilities.delete(srcs))
	def apply(srcs: Set[File]): Task[Unit] = Task(FileUtilities.delete(srcs))
}
import scala.collection.Set
trait ReadTracking[T] extends NotNull
{
	def dependsOn(file: T): Set[T]
	def products(file: T): Set[T]
	def sources(file: T): Set[T]
	def usedBy(file: T): Set[T]
	def allProducts: Set[T]
	def allSources: Set[T]
	def allUsed: Set[T]
	def allTags: Seq[(T,Array[Byte])]
}
import DependencyTracking.{DependencyMap => DMap, newMap, TagMap}
private final class DefaultTracking[T](translateProducts: Boolean)
	(val reverseDependencies: DMap[T], val reverseUses: DMap[T], val sourceMap: DMap[T], val tagMap: TagMap[T])
	extends DependencyTracking[T](translateProducts)
{
	val productMap: DMap[T] = forward(sourceMap) // map from a source to its products.  Keep in sync with sourceMap
}
// if translateProducts is true, dependencies on a product are translated to dependencies on a source
private abstract class DependencyTracking[T](translateProducts: Boolean) extends ReadTracking[T] with UpdateTracking[T]
{
	val reverseDependencies: DMap[T] // map from a file to the files that depend on it
	val reverseUses: DMap[T] // map from a file to the files that use it
	val sourceMap: DMap[T] // map from a product to its sources.  Keep in sync with productMap
	val productMap: DMap[T] // map from a source to its products.  Keep in sync with sourceMap
	val tagMap: TagMap[T]

	def read = this

	final def dependsOn(file: T): Set[T] = get(reverseDependencies, file)
	final def products(file: T): Set[T] = get(productMap, file)
	final def sources(file: T): Set[T] = get(sourceMap, file)
	final def usedBy(file: T): Set[T] = get(reverseUses, file)
	final def tag(file: T): Array[Byte] = tagMap.getOrElse(file, new Array[Byte](0))

	final def allProducts = Set() ++ sourceMap.keys
	final def allSources = Set() ++ productMap.keys
	final def allUsed = Set() ++ reverseUses.keys
	final def allTags = tagMap.toSeq

	private def get(map: DMap[T], value: T): Set[T] = map.getOrElse(value, Set.empty[T])

	final def dependency(sourceFile: T, dependsOn: T)
	{
		val actualDependencies =
			if(!translateProducts)
				Seq(dependsOn)
			else
				sourceMap.getOrElse(dependsOn, Seq(dependsOn))
		actualDependencies.foreach { actualDependency => reverseDependencies.add(actualDependency, sourceFile) }
	}
	final def product(sourceFile: T, product: T)
	{
		productMap.add(sourceFile, product)
		sourceMap.add(product, sourceFile)
	}
	final def use(sourceFile: T, usesFile: T) { reverseUses.add(usesFile, sourceFile) }
	final def tag(sourceFile: T, t: Array[Byte]) { tagMap(sourceFile) = t }

	final def removeAll(files: Iterable[T])
	{
		def remove(a: DMap[T], b: DMap[T], file: T): Unit =
			for(x <- a.removeKey(file)) b --= x
		def removeAll(a: DMap[T], b: DMap[T]): Unit =
			files.foreach { file => remove(a, b, file); remove(b, a, file) }

		removeAll(forward(reverseDependencies), reverseDependencies)
		removeAll(productMap, sourceMap)
		removeAll(forward(reverseUses), reverseUses)
		tagMap --= files
	}
	protected final def forward(map: DMap[T]): DMap[T] =
	{
		val f = newMap[T]
		for( (key, values) <- map; value <- values) f.add(value, key)
		f
	}
}
