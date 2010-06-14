/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package sbt

import java.io.{InputStream,OutputStream}

class NoInputCache[T] extends InputCache[T]
{
	def uptodate(in: T)(cacheStream: InputStream) =
		new CacheResult
		{
			def uptodate = true
			def update(outputStream: OutputStream) {}
		}
	def force(in: T)(outputStream: OutputStream) {}
}
class NoOutputCache[O](create: => O) extends OutputCache[O]
{
	def loadCached(cacheStream: InputStream) = create
	def update(out: O)(cacheStream: OutputStream) {}
}