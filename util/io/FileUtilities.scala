/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package xsbt

import OpenResource._
import ErrorHandling.translate

import java.io.{File, FileInputStream, InputStream, OutputStream}
import java.util.jar.{Attributes, JarEntry, JarFile, JarInputStream, JarOutputStream, Manifest}
import java.util.zip.{GZIPOutputStream, ZipEntry, ZipFile, ZipInputStream, ZipOutputStream}
import scala.collection.mutable.HashSet

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
	
	private[xsbt] def jars(dir: File): Iterable[File] = wrapNull(dir.listFiles(GlobFilter("*.jar")))
	
	def delete(files: Iterable[File]): Unit = files.foreach(delete)
	def delete(file: File)
	{
		translate("Error deleting file " + file + ": ")
		{
			if(file.isDirectory)
			{
				delete(wrapNull(file.listFiles))
				file.delete
			}
			else if(file.exists)
				file.delete
		}
	}
	private def wrapNull(a: Array[File]): Array[File] =
		if(a == null)
			new Array[File](0)
		else
			a
			
			
	/** Creates a jar file.
	* @param sources The files to include in the jar file.
	* @param outputJar The file to write the jar to.
	* @param manifest The manifest for the jar.
	* @param recursive If true, any directories in <code>sources</code> are recursively processed.
	* @param mapper The mapper that determines the name of a File in the jar. */
	def jar(sources: Iterable[File], outputJar: File, manifest: Manifest, recursive: Boolean, mapper: PathMapper): Unit =
		archive(sources, outputJar, Some(manifest), recursive, mapper)
	/** Creates a zip file.
	* @param sources The files to include in the jar file.
	* @param outputZip The file to write the zip to.
	* @param recursive If true, any directories in <code>sources</code> are recursively processed.  Otherwise,
	* they are not
	* @param mapper The mapper that determines the name of a File in the jar. */
	def zip(sources: Iterable[File], outputZip: File, recursive: Boolean, mapper: PathMapper): Unit =
		archive(sources, outputZip, None, recursive, mapper)
	
	private def archive(sources: Iterable[File], outputFile: File, manifest: Option[Manifest], recursive: Boolean, mapper: PathMapper)
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
				writeZip(sources, output, recursive, mapper)(createEntry)
			}
		}
	}
	private def writeZip(sources: Iterable[File], output: ZipOutputStream, recursive: Boolean, mapper: PathMapper)(createEntry: String => ZipEntry)
	{
		def add(sourceFile: File)
		{
			if(sourceFile.isDirectory)
			{
				if(recursive)
					wrapNull(sourceFile.listFiles).foreach(add)
			}
			else if(sourceFile.exists)
			{
				val name = mapper(sourceFile)
				val nextEntry = createEntry(name)
				nextEntry.setTime(sourceFile.lastModified)
				output.putNextEntry(nextEntry)
				transferAndClose(new FileInputStream(sourceFile), output)
			}
			else
				error("Source " + sourceFile + " does not exist.")
		}
		sources.foreach(add)
		output.closeEntry()
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
	def copy(sources: Iterable[File], destinationDirectory: File, mapper: PathMapper) =
	{
		val targetSet = new scala.collection.mutable.HashSet[File]
		copyImpl(sources, destinationDirectory) { from =>
			val to = new File(destinationDirectory, mapper(from))
			targetSet += to
			if(!to.exists || from.lastModified > to.lastModified)
			{
				if(from.isDirectory)
					createDirectory(to)
				else
				{
					//log.debug("Copying " + source + " to " + toPath)
					copyFile(from, to)
				}
			}
		}
		targetSet.readOnly
	}
	private def copyImpl(sources: Iterable[File], target: File)(doCopy: File => Unit)
	{
		if(!target.isDirectory)
			createDirectory(target)
		sources.toList.foreach(doCopy)
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
}
