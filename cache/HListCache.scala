/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package sbt

import java.io.{InputStream,OutputStream}

import Types._
class HNilInputCache extends NoInputCache[HNil]
class HConsInputCache[H,T <: HList](val headCache: InputCache[H], val tailCache: InputCache[T]) extends InputCache[H :+: T]
{
	def uptodate(in: H :+: T)(cacheStream: InputStream) =
	{
		val headResult = headCache.uptodate(in.head)(cacheStream)
		val tailResult = tailCache.uptodate(in.tail)(cacheStream)
		new CacheResult
		{
			val uptodate = headResult.uptodate && tailResult.uptodate
			def update(outputStream: OutputStream) =
			{
				headResult.update(outputStream)
				tailResult.update(outputStream)
			}
		}
	}
	def force(in: H :+: T)(cacheStream: OutputStream) =
	{
		headCache.force(in.head)(cacheStream)
		tailCache.force(in.tail)(cacheStream)
	}
}

class HNilOutputCache extends NoOutputCache[HNil](HNil)
class HConsOutputCache[H,T <: HList](val headCache: OutputCache[H], val tailCache: OutputCache[T]) extends OutputCache[H :+: T]
{
	def loadCached(cacheStream: InputStream) =
	{
		val head = headCache.loadCached(cacheStream)
		val tail = tailCache.loadCached(cacheStream)
		HCons(head, tail)
	}
	def update(out: H :+: T)(cacheStream: OutputStream)
	{
		headCache.update(out.head)(cacheStream)
		tailCache.update(out.tail)(cacheStream)
	}
}