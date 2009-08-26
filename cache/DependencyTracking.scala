package xsbt

import java.io.File
import sbinary.{Format, Operations}

object DependencyTracking
{
	def trackBasic[T, F <: FileInfo](filesTask: Task[Set[File]], style: FilesInfo.Style[F], cacheDirectory: File)
		(f: (ChangeReport[File], InvalidationReport[File], UpdateTracking[File]) => Task[T]): Task[T] =
	{
		changed(filesTask, style, new File(cacheDirectory, "files")) { sourceChanges =>
			invalidate(sourceChanges, cacheDirectory) { (report, tracking) =>
				f(sourceChanges, report, tracking)
			}
		}
	}
	def changed[T, F <: FileInfo](filesTask: Task[Set[File]], style: FilesInfo.Style[F], cache: File)(f: ChangeReport[File] => Task[T]): Task[T] =
		filesTask bind { files =>
			val lastFilesInfo = Operations.fromFile(cache)(style.format).files
			val lastFiles = lastFilesInfo.map(_.file)
			val currentFiles = files.map(_.getAbsoluteFile)
			val currentFilesInfo = style(files)

			val report = new ChangeReport[File]
			{
				lazy val allInputs = currentFiles
				lazy val removed = lastFiles -- allInputs
				lazy val added = allInputs -- lastFiles
				lazy val modified = (lastFilesInfo -- currentFilesInfo.files).map(_.file)
				lazy val unmodified = allInputs -- modified
			}

			f(report) map { result =>
				Operations.toFile(currentFilesInfo)(cache)(style.format)
				result
			}
		}
	def invalidate[R](changes: ChangeReport[File], cacheDirectory: File)(f: (InvalidationReport[File], UpdateTracking[File]) => Task[R]): Task[R] =
	{
		val pruneAndF = (report: InvalidationReport[File], tracking: UpdateTracking[File]) => {
			report.invalidProducts.foreach(_.delete)
			f(report, tracking)
		}
		invalidate(Task(changes), cacheDirectory, true)(pruneAndF)(sbinary.DefaultProtocol.FileFormat)
	}
	def invalidate[T,R](changesTask: Task[ChangeReport[T]], cacheDirectory: File, translateProducts: Boolean)
		(f: (InvalidationReport[T], UpdateTracking[T]) => Task[R])(implicit format: Format[T]): Task[R] =
	{
		changesTask bind { changes =>
			val trackFormat = new TrackingFormat[T](cacheDirectory, translateProducts)
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

			f(report, tracker) map { result =>
				trackFormat.write(tracker)
				result
			}
		}
	}

	import scala.collection.mutable.{Set, HashMap, MultiMap}
	private[xsbt] type DependencyMap[T] = HashMap[T, Set[T]] with MultiMap[T, T]
	private[xsbt] def newMap[T]: DependencyMap[T] = new HashMap[T, Set[T]] with MultiMap[T, T]
}

trait UpdateTracking[T] extends NotNull
{
	def dependency(source: T, dependsOn: T): Unit
	def use(source: T, uses: T): Unit
	def product(source: T, output: T): Unit
}
import scala.collection.Set
trait ReadTracking[T] extends NotNull
{
	def dependsOn(file: T): Set[T]
	def products(file: T): Set[T]
	def sources(file: T): Set[T]
	def usedBy(file: T): Set[T]
}
import DependencyTracking.{DependencyMap => DMap, newMap}
private final class DefaultTracking[T](translateProducts: Boolean)(val reverseDependencies: DMap[T], val reverseUses: DMap[T], val sourceMap: DMap[T]) extends DependencyTracking[T](translateProducts)
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

	final def dependsOn(file: T): Set[T] = get(reverseDependencies, file)
	final def products(file: T): Set[T] = get(productMap, file)
	final def sources(file: T): Set[T] = get(sourceMap, file)
	final def usedBy(file: T): Set[T] = get(reverseUses, file)

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

	final def removeAll(files: Iterable[T])
	{
		def remove(a: DMap[T], b: DMap[T], file: T): Unit =
			for(x <- a.removeKey(file)) b --= x
		def removeAll(a: DMap[T], b: DMap[T]): Unit =
			files.foreach { file => remove(a, b, file); remove(b, a, file) }

		removeAll(forward(reverseDependencies), reverseDependencies)
		removeAll(productMap, sourceMap)
		removeAll(forward(reverseUses), reverseUses)
	}
	protected final def forward(map: DMap[T]): DMap[T] =
	{
		val f = newMap[T]
		for( (key, values) <- map; value <- values) f.add(value, key)
		f
	}
}