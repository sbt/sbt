/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package xsbt

import OpenResource._
import ErrorHandling.translate

import java.io.{ByteArrayOutputStream, File, FileInputStream, InputStream, OutputStream}
import java.net.{URI, URISyntaxException, URL}
import java.nio.charset.Charset
import java.util.jar.{Attributes, JarEntry, JarFile, JarInputStream, JarOutputStream, Manifest}
import java.util.zip.{GZIPOutputStream, ZipEntry, ZipFile, ZipInputStream, ZipOutputStream}
import scala.collection.mutable.HashSet
import scala.reflect.{Manifest => SManifest}
import Function.tupled

object FileUtilities
{
	/** The maximum number of times a unique temporary filename is attempted to be created.*/
	private val MaximumTries = 10
	/** The producer of randomness for unique name generation.*/
	private lazy val random = new java.util.Random
	val temporaryDirectory = new File(System.getProperty("java.io.tmpdir"))
	/** The size of the byte or char buffer used in various methods.*/
	private val BufferSize = 8192
	private val Newline = System.getProperty("line.separator")

	def classLocation(cl: Class[_]): URL =
	{
		val codeSource = cl.getProtectionDomain.getCodeSource
		if(codeSource == null) error("No class location for " + cl)
		else codeSource.getLocation
	}
	def classLocationFile(cl: Class[_]): File = toFile(classLocation(cl))
	def classLocation[T](implicit mf: SManifest[T]): URL = classLocation(mf.erasure)
	def classLocationFile[T](implicit mf: SManifest[T]): File = classLocationFile(mf.erasure)

	def toFile(url: URL) =
		try { new File(url.toURI) }
		catch { case _: URISyntaxException => new File(url.getPath) }

	/** Converts the given URL to a File.  If the URL is for an entry in a jar, the File for the jar is returned. */
	def asFile(url: URL): File =
	{
		url.getProtocol match
		{
			case "file" => toFile(url)
			case "jar" =>
				val path = url.getPath
				val end = path.indexOf('!')
				new File(new URI(if(end == -1) path else path.substring(0, end)))
			case _ => error("Invalid protocol " + url.getProtocol)
		}
	}
	def assertDirectory(file: File) { assert(file.isDirectory, (if(file.exists) "Not a directory: " else "Directory not found: ") + file) }
	def assertDirectories(file: File*) { file.foreach(assertDirectory) }

	// "base.extension" -> (base, extension)
	def split(name: String): (String, String) =
	{
		val lastDot = name.lastIndexOf('.')
		if(lastDot >= 0)
			(name.substring(0, lastDot), name.substring(lastDot+1))
		else
			(name, "")
	}

	/** Creates a file at the given location.*/
	def touch(file: File)
	{
		createDirectory(file.getParentFile)
		val created = translate("Could not create file " + file) { file.createNewFile() }
		if(created)
			()
		else if(file.isDirectory)
			error("File exists and is a directory.")
		else if(!file.setLastModified(System.currentTimeMillis))
			error("Could not update last modified time for file " + file)
	}
	def createDirectory(dir: File): Unit =
	{
		def failBase = "Could not create directory " + dir
		if(dir.isDirectory || dir.mkdirs())
			()
		else if(dir.exists)
			error(failBase + ": file exists and is not a directory.")
		else
			error(failBase)
	}
	def unzip(from: File, toDirectory: File): Set[File] = unzip(from, toDirectory, AllPassFilter)
	def unzip(from: File, toDirectory: File, filter: NameFilter): Set[File] = fileInputStream(from)(in => unzip(in, toDirectory, filter))
	def unzip(from: InputStream, toDirectory: File, filter: NameFilter): Set[File] =
	{
		createDirectory(toDirectory)
		zipInputStream(from) { zipInput => extract(zipInput, toDirectory, filter) }
	}
	private def extract(from: ZipInputStream, toDirectory: File, filter: NameFilter) =
	{
		val set = new HashSet[File]
		def next()
		{
			val entry = from.getNextEntry
			if(entry == null)
				()
			else
			{
				val name = entry.getName
				if(filter.accept(name))
				{
					val target = new File(toDirectory, name)
					//log.debug("Extracting zip entry '" + name + "' to '" + target + "'")
					if(entry.isDirectory)
						createDirectory(target)
					else
					{
						set += target
						translate("Error extracting zip entry '" + name + "' to '" + target + "': ") {
							fileOutputStream(false)(target) { out => transfer(from, out) }
						}
					}
					//target.setLastModified(entry.getTime)
				}
				else
				{
					//log.debug("Ignoring zip entry '" + name + "'")
				}
				from.closeEntry()
				next()
			}
		}
		next()
		Set() ++ set
	}

	/** Copies all bytes from the given input stream to the given output stream.
	* Neither stream is closed.*/
	def transfer(in: InputStream, out: OutputStream): Unit = transferImpl(in, out, false)
	/** Copies all bytes from the given input stream to the given output stream.  The
	* input stream is closed after the method completes.*/
	def transferAndClose(in: InputStream, out: OutputStream): Unit = transferImpl(in, out, true)
	private def transferImpl(in: InputStream, out: OutputStream, close: Boolean)
	{
		try
		{
			val buffer = new Array[Byte](BufferSize)
			def read()
			{
				val byteCount = in.read(buffer)
				if(byteCount >= 0)
				{
					out.write(buffer, 0, byteCount)
					read()
				}
			}
			read()
		}
		finally { if(close) in.close }
	}

	/** Creates a temporary directory and provides its location to the given function.  The directory
	* is deleted after the function returns.*/
	def withTemporaryDirectory[T](action: File => T): T =
	{
		val dir = createTemporaryDirectory
		try { action(dir) }
		finally { delete(dir) }
	}
	def createTemporaryDirectory: File =
	{
		def create(tries: Int): File =
		{
			if(tries > MaximumTries)
				error("Could not create temporary directory.")
			else
			{
				val randomName = "sbt_" + java.lang.Integer.toHexString(random.nextInt)
				val f = new File(temporaryDirectory, randomName)

				try { createDirectory(f); f }
				catch { case e: Exception => create(tries + 1) }
			}
		}
		create(0)
	}

	private[xsbt] def jars(dir: File): Iterable[File] = listFiles(dir, GlobFilter("*.jar"))

	def delete(files: Iterable[File]): Unit = files.foreach(delete)
	def delete(file: File)
	{
		translate("Error deleting file " + file + ": ")
		{
			if(file.isDirectory)
			{
				delete(listFiles(file))
				file.delete
			}
			else if(file.exists)
				file.delete
		}
	}
	def listFiles(filter: java.io.FileFilter)(dir: File): Array[File] = wrapNull(dir.listFiles(filter))
	def listFiles(dir: File, filter: java.io.FileFilter): Array[File] = wrapNull(dir.listFiles(filter))
	def listFiles(dir: File): Array[File] = wrapNull(dir.listFiles())
	private def wrapNull(a: Array[File]) =
	{
		if(a == null)
			new Array[File](0)
		else
			a
	}


	/** Creates a jar file.
	* @param sources The files to include in the jar file paired with the entry name in the jar.
	* @param outputJar The file to write the jar to.
	* @param manifest The manifest for the jar.*/
	def jar(sources: Iterable[(File,String)], outputJar: File, manifest: Manifest): Unit =
		archive(sources, outputJar, Some(manifest))
	/** Creates a zip file.
	* @param sources The files to include in the zip file paired with the entry name in the zip.
	* @param outputZip The file to write the zip to.*/
	def zip(sources: Iterable[(File,String)], outputZip: File): Unit =
		archive(sources, outputZip, None)

	private def archive(sources: Iterable[(File,String)], outputFile: File, manifest: Option[Manifest])
	{
		if(outputFile.isDirectory)
			error("Specified output file " + outputFile + " is a directory.")
		else
		{
			val outputDir = outputFile.getParentFile
			createDirectory(outputDir)
			withZipOutput(outputFile, manifest)
			{ output =>
				val createEntry: (String => ZipEntry) = if(manifest.isDefined) new JarEntry(_) else new ZipEntry(_)
				writeZip(sources, output)(createEntry)
			}
		}
	}
	private def writeZip(sources: Iterable[(File,String)], output: ZipOutputStream)(createEntry: String => ZipEntry)
	{
		def add(sourceFile: File, name: String)
		{
			if(sourceFile.isDirectory)
				()
			else if(sourceFile.exists)
			{
				val nextEntry = createEntry(normalizeName(name))
				nextEntry.setTime(sourceFile.lastModified)
				output.putNextEntry(nextEntry)
				transferAndClose(new FileInputStream(sourceFile), output)
			}
			else
				error("Source " + sourceFile + " does not exist.")
		}
		sources.foreach(tupled(add))
		output.closeEntry()
	}
	private def normalizeName(name: String) =
	{
		val sep = File.separatorChar
		if(sep == '/') name else name.replace(sep, '/')
	}

	private def withZipOutput(file: File, manifest: Option[Manifest])(f: ZipOutputStream => Unit)
	{
		fileOutputStream(false)(file) { fileOut =>
			val (zipOut, ext) =
				manifest match
				{
					case Some(mf) =>
					{
						import Attributes.Name.MANIFEST_VERSION
						val main = mf.getMainAttributes
						if(!main.containsKey(MANIFEST_VERSION))
							main.put(MANIFEST_VERSION, "1.0")
						(new JarOutputStream(fileOut, mf), "jar")
					}
					case None => (new ZipOutputStream(fileOut), "zip")
				}
			try { f(zipOut) }
			catch { case e: Exception => "Error writing " + ext + ": " + e.toString }
			finally { zipOut.close }
		}
	}
	def relativize(base: File, file: File): Option[String] =
	{
		val pathString = file.getAbsolutePath
		baseFileString(base) flatMap
		{
			baseString =>
			{
				if(pathString.startsWith(baseString))
					Some(pathString.substring(baseString.length))
				else
					None
			}
		}
	}
	private def baseFileString(baseFile: File): Option[String] =
	{
		if(baseFile.isDirectory)
		{
			val cp = baseFile.getAbsolutePath
			assert(cp.length > 0)
			if(cp.charAt(cp.length - 1) == File.separatorChar)
				Some(cp)
			else
				Some(cp + File.separatorChar)
		}
		else
			None
	}
	def copy(sources: Iterable[(File,File)]): Set[File] = Set( sources.map(tupled(copyImpl)).toSeq.toArray : _*)
	private def copyImpl(from: File, to: File): File =
	{
		if(!to.exists || from.lastModified > to.lastModified)
		{
			if(from.isDirectory)
				createDirectory(to)
			else
			{
				createDirectory(to.getParentFile)
				copyFile(from, to)
			}
		}
		to
	}
	def copyFile(sourceFile: File, targetFile: File)
	{
		require(sourceFile.exists, "Source file '" + sourceFile.getAbsolutePath + "' does not exist.")
		require(!sourceFile.isDirectory, "Source file '" + sourceFile.getAbsolutePath + "' is a directory.")
		fileInputChannel(sourceFile) { in =>
			fileOutputChannel(targetFile) { out =>
				val copied = out.transferFrom(in, 0, in.size)
				if(copied != in.size)
					error("Could not copy '" + sourceFile + "' to '" + targetFile + "' (" + copied + "/" + in.size + " bytes copied)")
			}
		}
	}
	def defaultCharset = Charset.forName("UTF-8")
	def write(toFile: File, content: String): Unit = write(toFile, content, defaultCharset)
	def write(toFile: File, content: String, charset: Charset): Unit = write(toFile, content, charset, false)
	def write(file: File, content: String, charset: Charset, append: Boolean)
	{
		if(charset.newEncoder.canEncode(content))
			fileWriter(charset, append)(file) { w => w.write(content); None }
		else
			error("String cannot be encoded by charset " + charset.name)
	}
	def read(file: File): String = read(file, defaultCharset)
	def read(file: File, charset: Charset): String =
	{
		val out = new ByteArrayOutputStream(file.length.toInt)
		fileInputStream(file){ in => transfer(in, out) }
		out.toString(charset.name)
	}
	/** doesn't close the InputStream */
	def read(in: InputStream): String = read(in, defaultCharset)
	/** doesn't close the InputStream */
	def read(in: InputStream, charset: Charset): String =
	{
		val out = new ByteArrayOutputStream
		transfer(in, out)
		out.toString(charset.name)
	}
	def readBytes(file: File): Array[Byte] = fileInputStream(file)(readBytes)
	/** doesn't close the InputStream */
	def readBytes(in: InputStream): Array[Byte] =
	{
		val out = new ByteArrayOutputStream
		transfer(in, out)
		out.toByteArray
	}

	/** A pattern used to split a String by path separator characters.*/
	private val PathSeparatorPattern = java.util.regex.Pattern.compile(File.pathSeparator)

	/** Splits a String around path separator characters. */
	def pathSplit(s: String) = PathSeparatorPattern.split(s)
}
