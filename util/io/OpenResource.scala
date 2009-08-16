/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package xsbt

import java.io.{Closeable, File, FileInputStream, FileOutputStream, InputStream, OutputStream}
import java.io.{ByteArrayOutputStream, InputStreamReader, OutputStreamWriter}
import java.io.{BufferedReader, BufferedWriter, FileReader, FileWriter, Reader, Writer}
import java.util.zip.{GZIPInputStream, GZIPOutputStream}
import java.net.{URL, URISyntaxException}
import java.nio.charset.{Charset, CharsetDecoder, CharsetEncoder}
import java.nio.channels.FileChannel
import java.util.jar.{Attributes, JarEntry, JarFile, JarInputStream, JarOutputStream, Manifest}
import java.util.zip.{GZIPOutputStream, ZipEntry, ZipFile, ZipInputStream, ZipOutputStream}

import ErrorHandling.translate
import OpenResource._

object FileUtilities
{
	def createDirectory(dir: File): Unit =
		translate("Could not create directory " + dir + ": ")
		{
			if(dir.exists)
			{
				if(!dir.isDirectory)
					error("file exists and is not a directory.")
			}
			else if(!dir.mkdirs())
				error("<unknown error>")
		}
}

abstract class OpenResource[Source, T] extends NotNull
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
abstract class WrapOpenResource[Source, T](implicit srcMf: SManifest[Source], targetMf: SManifest[T]) extends OpenResource[Source, T]
{
	protected def label[S](m: SManifest[S]) = m.erasure.getSimpleName
	protected def openImpl(source: Source): T
	protected final def open(source: Source): T =
		translate("Error wrapping " + label(srcMf) + " in " + label(targetMf) + ": ") { openImpl(source) }
}
trait OpenFile[T] extends OpenResource[File, T]
{
	protected def openImpl(file: File): T
	protected final def open(file: File): T =
	{
		val parent = file.getParentFile
		if(parent != null)
			FileUtilities.createDirectory(parent)
		translate("Error opening " + file + ": ") { openImpl(file) }
	}
}
object OpenResource
{
	def wrap[Source, T<: Closeable](openF: Source => T)(implicit srcMf: SManifest[Source], targetMf: SManifest[T]): OpenResource[Source,T] =
		wrap(openF, _.close)
	def wrap[Source, T](openF: Source => T, closeF: T => Unit)(implicit srcMf: SManifest[Source], targetMf: SManifest[T]): OpenResource[Source,T] =
		new WrapOpenResource[Source, T]
		{
			def openImpl(source: Source) = openF(source)
			def close(t: T) = closeF(t)
		}

	def resource[Source, T <: Closeable](openF: Source => T): OpenResource[Source,T] =
		resource(openF, _.close)
	def resource[Source, T <: Closeable](openF: Source => T, closeF: T => Unit): OpenResource[Source,T] =
		new OpenResource[Source,T]
		{
			def open(s: Source) = openF(s)
			def close(s: T) = closeF(s)
		}
	def file[T <: Closeable](openF: File => T): OpenFile[T] = file(openF, _.close())
	def file[T](openF: File => T, closeF: T => Unit): OpenFile[T] =
		new OpenFile[T]
		{
			def openImpl(file: File) = openF(file)
			def close(t: T) = closeF(t)
		}

	def fileOutputStream(append: Boolean) = file(f => new FileOutputStream(f, append))
	def fileInputStream = file(f => new FileInputStream(f))
	def urlInputStream = resource( (u: URL) => translate("Error opening " + u + ": ")(u.openStream))
	def fileOutputChannel = file(f => new FileOutputStream(f).getChannel)
	def fileInputChannel = file(f => new FileInputStream(f).getChannel)
	def fileWriter(charset: Charset, append: Boolean) =
		file(f => new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, append), charset)) )
	def fileReader(charset: Charset) = file(f => new BufferedReader(new InputStreamReader(new FileInputStream(f), charset)) )
	def jarFile(verify: Boolean) = file(f => new JarFile(f, verify), (_: JarFile).close())
	def zipFile = file(f => new ZipFile(f), (_: ZipFile).close())
	def streamReader = wrap{ (_: (InputStream, Charset)) match { case (in, charset) => new InputStreamReader(in, charset) } }
	def gzipInputStream = wrap( (in: InputStream) => new GZIPInputStream(in) )
	def zipInputStream = wrap( (in: InputStream) => new ZipInputStream(in))
	def gzipOutputStream = wrap((out: OutputStream) => new GZIPOutputStream(out), (_: GZIPOutputStream).finish())
	def jarOutputStream = wrap( (out: OutputStream) => new JarOutputStream(out))
	def jarInputStream = wrap( (in: InputStream) => new JarInputStream(in))
	def zipEntry(zip: ZipFile) = resource( (entry: ZipEntry) =>
		translate("Error opening " + entry.getName + " in " + zip + ": ") { zip.getInputStream(entry) } )
}