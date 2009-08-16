package xsbt

import sbinary.Format
import sbinary.JavaIO._
import java.io.{File, InputStream, OutputStream}

trait CacheResult
{
	def uptodate: Boolean
	def update(stream: OutputStream): Unit
}
trait InputCache[I] extends NotNull
{
	def uptodate(in: I)(cacheStream: InputStream): CacheResult
	def force(in: I)(cacheStream: OutputStream): Unit
}
trait OutputCache[O] extends NotNull
{
	def loadCached(cacheStream: InputStream): O
	def update(out: O)(cacheStream: OutputStream): Unit
}
class SeparatedCache[I,O](input: InputCache[I], output: OutputCache[O]) extends Cache[I,O]
{
	def apply(file: File)(in: I) =
		try { applyImpl(file, in) }
		catch { case _: Exception => Right(update(file)(in)) }
	protected def applyImpl(file: File, in: I) =
	{
		OpenResource.fileInputStream(file) { stream =>
			val cache = input.uptodate(in)(stream)
			lazy val doUpdate = (result: O) =>
			{
				OpenResource.fileOutputStream(false)(file) { stream =>
					cache.update(stream)
					output.update(result)(stream)
				}
			}
			if(cache.uptodate)
				try { Left(output.loadCached(stream)) }
				catch { case _: Exception => Right(doUpdate) }
			else
				Right(doUpdate)
		}
	}
	protected def update(file: File)(in: I)(out: O)
	{
		OpenResource.fileOutputStream(false)(file) { stream =>
			input.force(in)(stream)
			output.update(out)(stream)
		}
	}
}
class BasicOutputCache[O](val format: Format[O]) extends OutputCache[O]
{
	def loadCached(cacheStream: InputStream): O = format.reads(cacheStream)
	def update(out: O)(cacheStream: OutputStream): Unit = format.writes(cacheStream, out)
}
class BasicInputCache[I](val format: Format[I], val equiv: Equiv[I]) extends InputCache[I]
{
	def uptodate(in: I)(cacheStream: InputStream) =
	{
		val loaded = format.reads(cacheStream)
		new CacheResult
		{
			val uptodate = equiv.equiv(in, loaded)
			def update(outputStream: OutputStream) = force(in)(outputStream)
		}
	}
	def force(in: I)(outputStream: OutputStream) = format.writes(outputStream, in)
}
class WrappedInputCache[I,DI](val convert: I => DI, val base: InputCache[DI]) extends InputCache[I]
{
	def uptodate(in: I)(cacheStream: InputStream) = base.uptodate(convert(in))(cacheStream)
	def force(in: I)(outputStream: OutputStream) = base.force(convert(in))(outputStream)
}
class WrappedOutputCache[O,DO](val convert: O => DO, val reverse: DO => O, val base: OutputCache[DO]) extends OutputCache[O]
{
	def loadCached(cacheStream: InputStream): O = reverse(base.loadCached(cacheStream))
	def update(out: O)(cacheStream: OutputStream): Unit = base.update(convert(out))(cacheStream)
}