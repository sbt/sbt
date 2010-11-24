/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah, Viktor Klang, Ross McDonald
 */
package sbt

import Using._
import ErrorHandling.translate

import java.io.{BufferedReader, ByteArrayOutputStream, BufferedWriter, File, FileInputStream, InputStream, OutputStream}
import java.net.{URI, URISyntaxException, URL}
import java.nio.charset.Charset
import java.util.Properties
import java.util.jar.{Attributes, JarEntry, JarFile, JarInputStream, JarOutputStream, Manifest}
import java.util.zip.{CRC32, GZIPOutputStream, ZipEntry, ZipFile, ZipInputStream, ZipOutputStream}
import scala.collection.immutable.TreeSet
import scala.collection.mutable.{HashMap,HashSet}
import scala.reflect.{Manifest => SManifest}
import Function.tupled

object IO
{
	/** The maximum number of times a unique temporary filename is attempted to be created.*/
	private val MaximumTries = 10
	/** The producer of randomness for unique name generation.*/
	private lazy val random = new java.util.Random
	val temporaryDirectory = new File(System.getProperty("java.io.tmpdir"))
	/** The size of the byte or char buffer used in various methods.*/
	private val BufferSize = 8192
	val Newline = System.getProperty("line.separator")

	val utf8 = Charset.forName("UTF-8")

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

	def touch(files: Traversable[File]): Unit = files.foreach(touch)
	/** Creates a file at the given location.*/
	def touch(file: File)
	{
		createDirectory(file.getParentFile)
		val created = translate("Could not create file " + file) { file.createNewFile() }
		if(created || file.isDirectory)
			()
		else if(!file.setLastModified(System.currentTimeMillis))
			error("Could not update last modified time for file " + file)
	}
	def createDirectories(dirs: Traversable[File]): Unit =
		dirs.foreach(createDirectory)
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

	/** Gzips the file 'in' and writes it to 'out'.  'in' cannot be the same file as 'out'. */
	def gzip(in: File, out: File)
	{
		require(in != out, "Input file cannot be the same as the output file.")
		Using.fileInputStream(in) { inputStream =>
			Using.fileOutputStream()(out) { outputStream =>
				gzip(inputStream, outputStream)
			}
		}
	}
	/** Gzips the InputStream 'in' and writes it to 'output'.  Neither stream is closed.*/
	def gzip(input: InputStream, output: OutputStream): Unit =
		gzipOutputStream(output) { gzStream => transfer(input, gzStream) }

	/** Gunzips the file 'in' and writes it to 'out'.  'in' cannot be the same file as 'out'. */
	def gunzip(in: File, out: File)
	{
		require(in != out, "Input file cannot be the same as the output file.")
		Using.fileInputStream(in) { inputStream =>
			Using.fileOutputStream()(out) { outputStream =>
				gunzip(inputStream, outputStream)
			}
		}
	}
	/** Gunzips the InputStream 'input' and writes it to 'output'.  Neither stream is closed.*/
	def gunzip(input: InputStream, output: OutputStream): Unit =
		gzipInputStream(input) { gzStream => transfer(gzStream, output) }

	def unzip(from: File, toDirectory: File, filter: NameFilter = AllPassFilter): Set[File] = fileInputStream(from)(in => unzipStream(in, toDirectory, filter))
	def unzipURL(from: URL, toDirectory: File, filter: NameFilter = AllPassFilter): Set[File] = urlInputStream(from)(in => unzipStream(in, toDirectory, filter))
	def unzipStream(from: InputStream, toDirectory: File, filter: NameFilter = AllPassFilter): Set[File] =
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

	/** Retrieves the content of the given URL and writes it to the given File. */
	def download(url: URL, to: File) =
		Using.urlInputStream(url) { inputStream =>
			transfer(inputStream, to)
		}

	def transfer(in: File, out: File): Unit =
		fileInputStream(in){ in => transfer(in, out) }

	def transfer(in: File, out: OutputStream): Unit =
		fileInputStream(in){ in => transfer(in, out) }

	/** Copies all bytes from the given input stream to the given File.*/
	def transfer(in: InputStream, to: File): Unit =
		Using.fileOutputStream()(to) { outputStream =>
			transfer(in, outputStream)
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
	def withTemporaryFile[T](prefix: String, postfix: String)(action: File => T): T =
	{
		val file = File.createTempFile(prefix, postfix)
		try { action(file) }
		finally { file.delete() }
	}

	private[sbt] def jars(dir: File): Iterable[File] = listFiles(dir, GlobFilter("*.jar"))

	def deleteIfEmpty(dirs: collection.Set[File]): Unit =
	{
		val isEmpty = new HashMap[File, Boolean]
		def visit(f: File): Boolean = isEmpty.getOrElseUpdate(f, dirs(f) && f.isDirectory && (f.listFiles forall visit) )

		dirs foreach visit
		for( (f, true) <- isEmpty) f.delete
	}

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
	private[sbt] def wrapNull(a: Array[File]) =
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
	def jar(sources: Traversable[(File,String)], outputJar: File, manifest: Manifest): Unit =
		archive(sources.toSeq, outputJar, Some(manifest))
	/** Creates a zip file.
	* @param sources The files to include in the zip file paired with the entry name in the zip.
	* @param outputZip The file to write the zip to.*/
	def zip(sources: Traversable[(File,String)], outputZip: File): Unit =
		archive(sources.toSeq, outputZip, None)

	private def archive(sources: Seq[(File,String)], outputFile: File, manifest: Option[Manifest])
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
	private def writeZip(sources: Seq[(File,String)], output: ZipOutputStream)(createEntry: String => ZipEntry)
	{
			import Path.{lazyPathFinder => pf}
		val files = sources.collect { case (file,name) if file.isFile => (file, normalizeName(name)) }
		val now = System.currentTimeMillis
		// The CRC32 for an empty value, needed to store directories in zip files
		val emptyCRC = new CRC32().getValue()

		def addDirectoryEntry(name: String)
		{
			output putNextEntry makeDirectoryEntry(name)
			output.closeEntry()
		}

		def makeDirectoryEntry(name: String) =
		{
//			log.debug("\tAdding directory " + relativePath + " ...")
			val e = createEntry(name)
			e setTime now
			e setSize 0
			e setMethod ZipEntry.STORED
			e setCrc emptyCRC
			e
		}

		def makeFileEntry(file: File, name: String) =
		{
//			log.debug("\tAdding " + file + " as " + name + " ...")
			val e = createEntry(name)
			e setTime file.lastModified
			e
		}
		def addFileEntry(file: File, name: String)
		{
			output putNextEntry makeFileEntry(file, name)
			transfer(file, output)
			output.closeEntry()
		}

		//Calculate directories and add them to the generated Zip
		allDirectoryPaths(files) foreach addDirectoryEntry

		//Add all files to the generated Zip
		files foreach { case (file, name) => addFileEntry(file, name) }
	}

	// map a path a/b/c to List("a", "b")
	private def relativeComponents(path: String): List[String] =
		path.split("/").toList.dropRight(1)

	// map components List("a", "b", "c") to List("a/b/c/", "a/b/", "a/", "")
	private def directories(path: List[String]): List[String] =
		path.foldLeft(List(""))( (e,l) => (e.head + l + "/") :: e )

	// map a path a/b/c to List("a/b/", "a/")
	private def directoryPaths(path: String): List[String] =
		directories(relativeComponents(path)).filter(_.length > 1)

	// produce a sorted list of all the subdirectories of all provided files
	private def allDirectoryPaths(files: Iterable[(File,String)]) =
		TreeSet[String]() ++ (files flatMap { case (file, name) => directoryPaths(name) })

	private def normalizeDirName(name: String) =
	{
		val norm1 = normalizeName(name)
		if(norm1.endsWith("/")) norm1 else (norm1 + "/")
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
			val normalized = if(cp.charAt(cp.length - 1) == File.separatorChar) cp else cp + File.separatorChar
			Some(normalized)
		}
		else
			None
	}
	def copy(sources: Traversable[(File,File)], overwrite: Boolean = false, preserveLastModified: Boolean = false): Set[File] =
		sources.map( tupled(copyImpl(overwrite, preserveLastModified)) ).toSet
	private def copyImpl(overwrite: Boolean, preserveLastModified: Boolean)(from: File, to: File): File =
	{
		if(overwrite || !to.exists || from.lastModified > to.lastModified)
		{
			if(from.isDirectory)
				createDirectory(to)
			else
			{
				createDirectory(to.getParentFile)
				copyFile(from, to, preserveLastModified)
			}
		}
		to
	}
	def copyDirectory(source: File, target: File, overwrite: Boolean = false, preserveLastModified: Boolean = false): Unit =
		copy( (Path.fromFile(source) ***) x Path.rebase(source, target), overwrite, preserveLastModified)

	def copyFile(sourceFile: File, targetFile: File, preserveLastModified: Boolean = false)
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
		if(preserveLastModified)
			copyLastModified(sourceFile, targetFile)
	}
	def copyLastModified(sourceFile: File, targetFile: File) = targetFile.setLastModified( sourceFile.lastModified )
	def defaultCharset = utf8

	def write(file: File, content: String, charset: Charset = defaultCharset, append: Boolean = false): Unit =
		writer(file, content, charset, append) { _.write(content)  }

	def writer[T](file: File, content: String, charset: Charset, append: Boolean = false)(f: BufferedWriter => T): T =
	{
		if(charset.newEncoder.canEncode(content))
			fileWriter(charset, append)(file) { f }
		else
			error("String cannot be encoded by charset " + charset.name)
	}

	def reader[T](file: File, charset: Charset = defaultCharset)(f: BufferedReader => T): T =
		fileReader(charset)(file) { f }

	def read(file: File, charset: Charset = defaultCharset): String =
	{
		val out = new ByteArrayOutputStream(file.length.toInt)
		transfer(file, out)
		out.toString(charset.name)
	}
	/** doesn't close the InputStream */
	def readStream(in: InputStream, charset: Charset = defaultCharset): String =
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

	def append(file: File, content: String, charset: Charset = defaultCharset): Unit =
		write(file, content, charset, true)
	def append(file: File, bytes: Array[Byte]): Unit =
		writeBytes(file, bytes, true)

	def write(file: File, bytes: Array[Byte]): Unit =
		writeBytes(file, bytes, false)
	private def writeBytes(file: File, bytes: Array[Byte], append: Boolean): Unit =
		fileOutputStream(append)(file) { _.write(bytes) }


	// Not optimized for large files
	def readLines(file: File, charset: Charset = defaultCharset): List[String] =
		fileReader(charset)(file)(readLines)
		
	// Not optimized for large files
	def readLines(in: BufferedReader): List[String] = 
		foldLines[List[String]](in, Nil)( (accum, line) => line :: accum ).reverse
	
	def foreachLine(in: BufferedReader)(f: String => Unit): Unit =
		foldLines(in, ())( (_, line) => f(line) )
		
	def foldLines[T](in: BufferedReader, init: T)(f: (T, String) => T): T =
	{
		def readLine(accum: T): T =
		{
			val line = in.readLine()
			if(line eq null) accum else readLine(f(accum, line))
		}
		readLine(init)
	}
	
	def writeLines(file: File, lines: Seq[String], charset: Charset = defaultCharset, append: Boolean = false): Unit =
		writer(file, lines.headOption.getOrElse(""), charset, append) { w =>
			lines.foreach { line => w.write(line); w.newLine() }
		}
		
	def write(properties: Properties, label: String, to: File) =
		fileOutputStream()(to) { output => properties.store(output, label) }
	def load(properties: Properties, from: File): Unit =
		if(from.exists)
			fileInputStream(from){ input => properties.load(input) }

	/** A pattern used to split a String by path separator characters.*/
	private val PathSeparatorPattern = java.util.regex.Pattern.compile(File.pathSeparator)

	/** Splits a String around path separator characters. */
	def pathSplit(s: String) = PathSeparatorPattern.split(s)

	/** Move the provided files to a temporary location.
	*   If 'f' returns normally, delete the files.
	*   If 'f' throws an Exception, return the files to their original location.*/
	def stash[T](files: Set[File])(f: => T): T =
		withTemporaryDirectory { dir =>
			val stashed = stashLocations(dir, files.toArray)
			move(stashed)

			try { f } catch { case e: Exception =>
				try { move(stashed.map(_.swap)); throw e }
				catch { case _: Exception => throw e }
			}
		}

	private def stashLocations(dir: File, files: Array[File]) =
		for( (file, index) <- files.zipWithIndex) yield
			(file, new File(dir, index.toHexString))

	def move(files: Traversable[(File, File)]): Unit =
		files.foreach(Function.tupled(move))
		
	def move(a: File, b: File): Unit =
	{
		if(b.exists)
			delete(b)
		if(!a.renameTo(b))
		{
			copyFile(a, b, true)
			delete(a)
		}
	}

	def gzipFileOut[T](file: File)(f: OutputStream => T): T =
		Using.fileOutputStream()(file) { fout =>
		Using.gzipOutputStream(fout) { outg =>
		Using.bufferedOutputStream(outg)(f) }}

	def gzipFileIn[T](file: File)(f: InputStream => T): T =
		Using.fileInputStream(file) { fin =>
		Using.gzipInputStream(fin) { ing =>
		Using.bufferedInputStream(ing)(f) }}
}
