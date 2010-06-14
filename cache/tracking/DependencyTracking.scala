/* sbt -- Simple Build Tool
 * Copyright 2009, 2010 Mark Harrah
 */
package sbt

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
	// removes files from all maps, both keys and values
	def removeAll(files: Iterable[T]): Unit
	// removes sources as keys/values in source, product maps and as values in reverseDependencies map
	def pending(sources: Iterable[T]): Unit
}
trait ReadTracking[T] extends NotNull
{
	def isProduct(file: T): Boolean
	def isSource(file: T): Boolean
	def isUsed(file: T): Boolean
	def dependsOn(file: T): Set[T]
	def products(file: T): Set[T]
	def sources(file: T): Set[T]
	def usedBy(file: T): Set[T]
	def tag(file: T): Array[Byte]
	def allProducts: Set[T]
	def allSources: Set[T]
	def allUsed: Set[T]
	def allTags: Seq[(T,Array[Byte])]
}
import DependencyTracking.{DependencyMap => DMap, newMap, newTagMap, TagMap}
private object DefaultTracking
{
	def apply[T](translateProducts: Boolean): DependencyTracking[T] =
		new DefaultTracking(translateProducts)(newMap, newMap, newMap, newTagMap)
}
private final class DefaultTracking[T](translateProducts: Boolean)
	(val reverseDependencies: DMap[T], val reverseUses: DMap[T], val sourceMap: DMap[T], val tagMap: TagMap[T])
	extends DependencyTracking[T](translateProducts)
{
	val productMap: DMap[T] = forward(sourceMap) // map from a source to its products.  Keep in sync with sourceMap
}
// if translateProducts is true, dependencies on a product are translated to dependencies on a source
//   if there is a source recorded as generating that product
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

	def isProduct(file: T): Boolean = exists(sourceMap, file)
	def isSource(file: T): Boolean = exists(productMap, file)
	def isUsed(file: T): Boolean = exists(reverseUses, file)


	final def allProducts = sourceMap.keysIterator.toSet
	final def allSources = productMap.keysIterator.toSet
	final def allUsed = reverseUses.keysIterator.toSet
	final def allTags = tagMap.toSeq

	private def exists(map: DMap[T], value: T): Boolean = map.contains(value)
	private def get(map: DMap[T], value: T): Set[T] = map.getOrElse[collection.Set[T]](value, Set.empty[T]).toSet

	final def dependency(sourceFile: T, dependsOn: T)
	{
		val actualDependencies =
			if(!translateProducts)
				Seq(dependsOn)
			else
				sourceMap.getOrElse[Iterable[T]](dependsOn, Seq(dependsOn))
		actualDependencies.foreach { actualDependency => reverseDependencies.add(actualDependency, sourceFile) }
	}
	final def product(sourceFile: T, product: T)
	{
		productMap.add(sourceFile, product)
		sourceMap.add(product, sourceFile)
	}
	final def use(sourceFile: T, usesFile: T) { reverseUses.add(usesFile, sourceFile) }
	final def tag(sourceFile: T, t: Array[Byte]) { tagMap(sourceFile) = t }

	private def removeOneWay(a: DMap[T], files: Iterable[T]): Unit =
		a.values.foreach { _ --= files }
	private def remove(a: DMap[T], b: DMap[T], file: T): Unit =
		for(x <- a.removeKey(file)) b --= x
	private def removeAll(files: Iterable[T], a: DMap[T], b: DMap[T]): Unit =
		files.foreach { file => remove(a, b, file); remove(b, a, file) }
	final def removeAll(files: Iterable[T])
	{
		removeAll(files, forward(reverseDependencies), reverseDependencies)
		removeAll(files, productMap, sourceMap)
		removeAll(files, forward(reverseUses), reverseUses)
		tagMap --= files
	}
	def pending(sources: Iterable[T])
	{
		removeOneWay(reverseDependencies, sources)
		removeOneWay(reverseUses, sources)
		removeAll(sources, productMap, sourceMap)
		tagMap --= sources
	}
	protected final def forward(map: DMap[T]): DMap[T] =
	{
		val f = newMap[T]
		for( (key, values) <- map; value <- values) f.add(value, key)
		f
	}
	override def toString = 
		(graph("Reverse source dependencies", reverseDependencies) ::
		graph("Sources and products", productMap) ::
		graph("Reverse uses", reverseUses) ::
		Nil) mkString "\n"
	def graph(title: String, map: DMap[T]) =
		"\"" + title + "\" {\n\t" + graphEntries(map) + "\n}"
	def graphEntries(map: DMap[T]) = map.map{ case (key, values) => values.map(key + " -> " + _).mkString("\n\t") }.mkString("\n\t")
}
