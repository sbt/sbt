/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package sbt

import java.io.{Closeable, File, FileInputStream, FileOutputStream, InputStream, OutputStream}
import java.io.{BufferedInputStream, BufferedOutputStream, ByteArrayOutputStream, InputStreamReader, OutputStreamWriter}
import java.io.{BufferedReader, BufferedWriter, FileReader, FileWriter, Reader, Writer}
import java.util.zip.{GZIPInputStream, GZIPOutputStream}
import java.net.{URL, URISyntaxException}
import java.nio.charset.{Charset, CharsetDecoder, CharsetEncoder}
import java.nio.channels.FileChannel
import java.util.jar.{Attributes, JarEntry, JarFile, JarInputStream, JarOutputStream, Manifest}
import java.util.zip.{GZIPOutputStream, ZipEntry, ZipFile, ZipInputStream, ZipOutputStream}

import ErrorHandling.translate
import Using._

abstract class Using[Source, T]
{
	protected def open(src: Source): T
	def apply[R](src: Source)(f: T => R): R =
	{
		val resource = open(src)
		try { f(resource) }
		finally { close(resource) }
	}
	protected def close(out: T): Unit
}
import scala.reflect.{Manifest => SManifest}
abstract class WrapUsing[Source, T](implicit srcMf: SManifest[Source], targetMf: SManifest[T]) extends Using[Source, T]
{
	protected def label[S](m: SManifest[S]) = m.erasure.getSimpleName
	protected def openImpl(source: Source): T
	protected final def open(source: Source): T =
		translate("Error wrapping " + label(srcMf) + " in " + label(targetMf) + ": ") { openImpl(source) }
}
trait OpenFile[T] extends Using[File, T]
{
	protected def openImpl(file: File): T
	protected final def open(file: File): T =
	{
		val parent = file.getParentFile
		if(parent != null)
			IO.createDirectory(parent)
		openImpl(file)
	}
}
object Using
{
	def wrap[Source, T<: Closeable](openF: Source => T)(implicit srcMf: SManifest[Source], targetMf: SManifest[T]): Using[Source,T] =
		wrap(openF, closeCloseable)
	def wrap[Source, T](openF: Source => T, closeF: T => Unit)(implicit srcMf: SManifest[Source], targetMf: SManifest[T]): Using[Source,T] =
		new WrapUsing[Source, T]
		{
			def openImpl(source: Source) = openF(source)
			def close(t: T) = closeF(t)
		}

	def resource[Source, T <: Closeable](openF: Source => T): Using[Source,T] =
		resource(openF, closeCloseable)
	def resource[Source, T](openF: Source => T, closeF: T => Unit): Using[Source,T] =
		new Using[Source,T]
		{
			def open(s: Source) = openF(s)
			def close(s: T) = closeF(s)
		}
	def file[T <: Closeable](openF: File => T): OpenFile[T] = file(openF, closeCloseable)
	def file[T](openF: File => T, closeF: T => Unit): OpenFile[T] =
		new OpenFile[T]
		{
			def openImpl(file: File) = openF(file)
			def close(t: T) = closeF(t)
		}
	private def closeCloseable[T <: Closeable]: T => Unit = _.close()

	def bufferedOutputStream = wrap( (out: OutputStream) => new BufferedOutputStream(out) )
	def bufferedInputStream = wrap( (in: InputStream) => new BufferedInputStream(in) )
	def fileOutputStream(append: Boolean = false) = file(f => new BufferedOutputStream(new FileOutputStream(f, append)))
	def fileInputStream = file(f => new BufferedInputStream(new FileInputStream(f)))
	def urlInputStream = resource( (u: URL) => translate("Error opening " + u + ": ")(u.openStream))
	def fileOutputChannel = file(f => new FileOutputStream(f).getChannel)
	def fileInputChannel = file(f => new FileInputStream(f).getChannel)
	def fileWriter(charset: Charset = IO.utf8, append: Boolean = false) =
		file(f => new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, append), charset)) )
	def fileReader(charset: Charset) = file(f => new BufferedReader(new InputStreamReader(new FileInputStream(f), charset)) )
	def jarFile(verify: Boolean) = file(f => new JarFile(f, verify), (_: JarFile).close())
	def zipFile = file(f => new ZipFile(f), (_: ZipFile).close())
	def streamReader = wrap{ (_: (InputStream, Charset)) match { case (in, charset) => new InputStreamReader(in, charset) } }
	def gzipInputStream = wrap( (in: InputStream) => new GZIPInputStream(in, 8192) )
	def zipInputStream = wrap( (in: InputStream) => new ZipInputStream(in))
	def zipOutputStream = wrap( (out: OutputStream) => new ZipOutputStream(out))
	def gzipOutputStream = wrap((out: OutputStream) => new GZIPOutputStream(out, 8192), (_: GZIPOutputStream).finish())
	def jarOutputStream = wrap( (out: OutputStream) => new JarOutputStream(out))
	def jarInputStream = wrap( (in: InputStream) => new JarInputStream(in))
	def zipEntry(zip: ZipFile) = resource( (entry: ZipEntry) =>
		translate("Error opening " + entry.getName + " in " + zip + ": ") { zip.getInputStream(entry) } )
}