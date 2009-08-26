package xsbt

import java.io.File
import scala.collection.mutable.{HashMap, Map, MultiMap, Set}
import sbinary.{DefaultProtocol, Format, Operations}
import DefaultProtocol._
import TrackingFormat._
import DependencyTracking.{DependencyMap => DMap, newMap}

private class TrackingFormat[T](directory: File, translateProducts: Boolean)(implicit tFormat: Format[T]) extends NotNull
{

	val indexFile = new File(directory, "index")
	val dependencyFile = new File(directory, "dependencies")
	def read(): DependencyTracking[T] =
	{
		val indexMap = Operations.fromFile[Map[Int,T]](indexFile)
		val indexedFormat = wrap[T,Int](ignore => error("Read-only"), indexMap.apply)
		Operations.fromFile(dependencyFile)(trackingFormat(translateProducts)(indexedFormat))
	}
	def write(tracking: DependencyTracking[T])
	{
		val index = new IndexMap[T]
		val indexedFormat = wrap[T,Int](t => index(t), ignore => error("Write-only"))

		Operations.toFile(tracking)(dependencyFile)(trackingFormat(translateProducts)(indexedFormat))
		Operations.toFile(index.indices)(indexFile)
	}
}
private object TrackingFormat
{
	 implicit def mutableMapFormat[S, T](implicit binS : Format[S], binT : Format[T]) : Format[Map[S, T]] =
		viaArray( (x : Array[(S, T)]) => Map(x :_*));
	 implicit def depMapFormat[T](implicit bin: Format[T]) : Format[DMap[T]] =
	{
		viaArray { (x : Array[(T, Set[T])]) =>
			val map = newMap[T]
			map ++= x
			map
		}
	}
	def trackingFormat[T](translateProducts: Boolean)(implicit tFormat: Format[T]): Format[DependencyTracking[T]] =
		asProduct3((a: DMap[T],b: DMap[T],c: DMap[T]) => new DefaultTracking(translateProducts)(a,b,c) : DependencyTracking[T])(dt => Some(dt.reverseDependencies, dt.reverseUses, dt.sourceMap))
}

private final class IndexMap[T] extends NotNull
{
	private[this] var lastIndex = 0
	private[this] val map = new HashMap[T, Int]
	def indices = map.toArray.map( (_: (T,Int)).swap )
	def apply(t: T) = map.getOrElseUpdate(t, { lastIndex += 1; lastIndex })
}