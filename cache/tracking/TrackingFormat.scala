/* sbt -- Simple Build Tool
 * Copyright 2009, 2010 Mark Harrah
 */
package sbt

import java.io.File
import scala.collection.mutable.{HashMap, Map, MultiMap, Set}
import scala.reflect.Manifest
import sbinary.{DefaultProtocol, Format}
import DefaultProtocol._
import TrackingFormat._
import CacheIO.{fromFile, toFile}
import DependencyTracking.{DependencyMap => DMap, newMap, TagMap}

private class TrackingFormat[T](directory: File, translateProducts: Boolean)(implicit tFormat: Format[T], mf: Manifest[T]) extends NotNull
{
	val indexFile = new File(directory, "index")
	val dependencyFile = new File(directory, "dependencies")
	def read(): DependencyTracking[T] =
	{
		val indexMap = CacheIO.fromFile[Map[Int,T]](indexFile, new HashMap[Int,T])
		val indexedFormat = wrap[T,Int](ignore => error("Read-only"), i => indexMap.getOrElse(i, error("Index " + i + " not found")))
		val trackFormat = trackingFormat(translateProducts)(indexedFormat)
		fromFile(trackFormat, DefaultTracking[T](translateProducts))(dependencyFile)
	}
	def write(tracking: DependencyTracking[T])
	{
		val index = new IndexMap[T]
		val indexedFormat = wrap[T,Int](t => index(t), ignore => error("Write-only"))
		val trackFormat = trackingFormat(translateProducts)(indexedFormat)
		toFile(trackFormat)(tracking)(dependencyFile)
		toFile(index.indices)(indexFile)
	}
}
private object TrackingFormat
{
	 implicit def mutableMapFormat[S, T](implicit binS : Format[S], binT : Format[T]) : Format[HashMap[S, T]] =
		new LengthEncoded[HashMap[S, T], (S, T)] {
			def build(size : Int, ts : Iterator[(S, T)]) : HashMap[S, T] = {
				val b = new HashMap[S, T]
				b ++= ts
				b
			}
		}
	 implicit def depMapFormat[T](implicit bin: Format[T]) : Format[DMap[T]] =
		new LengthEncoded[DMap[T], (T, Set[T])] {
			def build(size : Int, ts : Iterator[(T, Set[T])]) : DMap[T] = {
				val b = newMap[T]
				b ++= ts
				b
			}
		}
	def trackingFormat[T](translateProducts: Boolean)(implicit tFormat: Format[T]): Format[DependencyTracking[T]] =
		asProduct4((a: DMap[T],b: DMap[T],c: DMap[T], d:TagMap[T]) => new DefaultTracking(translateProducts)(a,b,c,d) : DependencyTracking[T]
			)(dt => (dt.reverseDependencies, dt.reverseUses, dt.sourceMap, dt.tagMap))
}

private final class IndexMap[T] extends NotNull
{
	private[this] var lastIndex = 0
	private[this] val map = new HashMap[T, Int]
	private[this] def nextIndex = { lastIndex += 1; lastIndex }
	def indices = HashMap(map.map( (_: (T,Int)).swap ).toSeq : _*)
	def apply(t: T) = map.getOrElseUpdate(t, nextIndex)
}