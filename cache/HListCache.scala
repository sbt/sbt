package xsbt

import java.io.{InputStream,OutputStream}
import metascala.HLists.{HCons,HList,HNil}

class HNilInputCache extends NoInputCache[HNil]
class HConsInputCache[H,T <: HList](val headCache: InputCache[H], val tailCache: InputCache[T]) extends InputCache[HCons[H,T]]
{
	def uptodate(in: HCons[H,T])(cacheStream: InputStream) =
	{
		lazy val headResult = headCache.uptodate(in.head)(cacheStream)
		lazy val tailResult = tailCache.uptodate(in.tail)(cacheStream)
		new CacheResult
		{
			lazy val uptodate = headResult.uptodate && tailResult.uptodate
			def update(outputStream: OutputStream) =
			{
				headResult.update(outputStream)
				tailResult.update(outputStream)
			}
		}
	}
	def force(in: HCons[H,T])(cacheStream: OutputStream) =
	{
		headCache.force(in.head)(cacheStream)
		tailCache.force(in.tail)(cacheStream)
	}
}

class HNilOutputCache extends NoOutputCache[HNil](HNil)
class HConsOutputCache[H,T <: HList](val headCache: OutputCache[H], val tailCache: OutputCache[T]) extends OutputCache[HCons[H,T]]
{
	def loadCached(cacheStream: InputStream) =
	{
		val head = headCache.loadCached(cacheStream)
		val tail = tailCache.loadCached(cacheStream)
		HCons(head, tail)
	}
	def update(out: HCons[H,T])(cacheStream: OutputStream)
	{
		headCache.update(out.head)(cacheStream)
		tailCache.update(out.tail)(cacheStream)
	}
}