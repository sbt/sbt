/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah, Nathan Hamblen
 */
package sbt

import java.io.{Closeable, File, FileInputStream, FileOutputStream, InputStream, OutputStream}
import java.io.{ByteArrayOutputStream, InputStreamReader, OutputStreamWriter}
import java.io.{BufferedReader, BufferedWriter, FileReader, FileWriter, Reader, Writer}
import java.util.zip.{GZIPInputStream, GZIPOutputStream}
import java.net.URL
import java.nio.charset.{Charset, CharsetDecoder, CharsetEncoder}
import java.nio.channels.FileChannel
import java.util.jar.{Attributes, JarEntry, JarFile, JarInputStream, JarOutputStream, Manifest}
import java.util.zip.{GZIPOutputStream, ZipEntry, ZipFile, ZipInputStream, ZipOutputStream}

import OpenResource._

final class Preserved private[sbt](toRestore: scala.collection.Map[File, Path], temp: File) extends NotNull
{
	def restore(log: Logger) =
	{
		try
		{
			Control.lazyFold(toRestore.toList) { case (src, dest) =>
				FileUtilities.copyFile(src, dest.asFile, log)
			}
		}
		finally { FileUtilities.clean(Path.fromFile(temp) :: Nil, true, log) }
	}
}

/** A collection of file related methods. */
object FileUtilities
{
	import wrap.Wrappers.readOnly
	/** The size of the byte or char buffer used in various methods.*/
	private val BufferSize = 8192
	private val Newline = System.getProperty("line.separator")
	/** A pattern used to split a String by path separator characters.*/
	private val PathSeparatorPattern = java.util.regex.Pattern.compile(File.pathSeparator)

	/** Splits a String around path separator characters. */
	private[sbt] def pathSplit(s: String) = PathSeparatorPattern.split(s)

	def preserve(paths: Iterable[Path], log: Logger): Either[String, Preserved] =
	{
		for(tmp <- createTemporaryDirectory(log).right) yield
		{
			val pathMap = new scala.collection.mutable.HashMap[File, Path]
			val destinationDirectory = Path.fromFile(tmp)
			for(source <- paths)
			{
				val toPath = Path.fromString(destinationDirectory, source.relativePath)
				copyFile(source, toPath, log)
				pathMap(toPath.asFile) = source
			}
			new Preserved(readOnly(pathMap), tmp)
		}
	}
	
	/** Gzips the file 'in' and writes it to 'out'.  'in' cannot be the same file as 'out'. */
	def gzip(in: Path, out: Path, log: Logger): Option[String] =
	{
		require(in != out, "Input file cannot be the same as the output file.")
		readStream(in.asFile, log) { inputStream =>
			writeStream(out.asFile, log) { outputStream =>
				gzip(inputStream, outputStream, log)
			}
		}
	}
	/** Gzips the InputStream 'in' and writes it to 'output'.  Neither stream is closed.*/
	def gzip(input: InputStream, output: OutputStream, log: Logger): Option[String] =
		gzipOutputStream.ioOption(output, "gzipping", log) { gzStream => transfer(input, gzStream, log) }

	def gunzip(input: InputStream, output: OutputStream, log: Logger): Option[String] =
		gzipInputStream.ioOption(input, "gunzipping", log) { gzStream => transfer(gzStream, output, log) }
	/** Gunzips the file 'in' and writes it to 'out'.  'in' cannot be the same file as 'out'. */
	def gunzip(in: Path, out: Path, log: Logger): Option[String] =
	{
		require(in != out, "Input file cannot be the same as the output file.")
		readStream(in.asFile, log) { inputStream =>
			writeStream(out.asFile, log) { outputStream =>
				gunzip(inputStream, outputStream, log)
			}
		}
	}
	
	/** Creates a jar file.
	* @param sources The files to include in the jar file.  The path used for the jar is
	* relative to the base directory for the source.  That is, the path in the jar for source
	* <code>(basePath ##) / x / y</code> is <code>x / y</code>.
	* @param outputJar The file to write the jar to.
	* @param manifest The manifest for the jar.
	* @param recursive If true, any directories in <code>sources</code> are recursively processed.  Otherwise,
	* they are not
	* @param log The Logger to use. */
	def jar(sources: Iterable[Path], outputJar: Path, manifest: Manifest, recursive: Boolean, log: Logger) =
		archive(sources, outputJar, Some(manifest), recursive, log)
	@deprecated def pack(sources: Iterable[Path], outputJar: Path, manifest: Manifest, recursive: Boolean, log: Logger) =
		jar(sources, outputJar, manifest, recursive, log)
	/** Creates a zip file.
	* @param sources The files to include in the jar file.  The path used for the jar is
	* relative to the base directory for the source.  That is, the path in the jar for source
	* <code>(basePath ##) / x / y</code> is <code>x / y</code>.
	* @param outputZip The file to write the zip to.
	* @param recursive If true, any directories in <code>sources</code> are recursively processed.  Otherwise,
	* they are not
	* @param log The Logger to use. */
	def zip(sources: Iterable[Path], outputZip: Path, recursive: Boolean, log: Logger) =
		archive(sources, outputZip, None, recursive, log)
	
	private def archive(sources: Iterable[Path], outputPath: Path, manifest: Option[Manifest], recursive: Boolean, log: Logger) =
	{
		log.info("Packaging " + outputPath + " ...")
		val outputFile = outputPath.asFile
		if(outputFile.isDirectory)
			Some("Specified output file " + outputFile + " is a directory.")
		else
		{
			val outputDir = outputFile.getParentFile
			val result = createDirectory(outputDir, log) orElse
				withZipOutput(outputFile, manifest, log)
				{ output =>
					val createEntry: (String => ZipEntry) = if(manifest.isDefined) new JarEntry(_) else new ZipEntry(_)
					writeZip(sources, output, recursive, log)(createEntry)
				}
			if(result.isEmpty)
				log.info("Packaging complete.")
			result
		}
	}
	
	private def writeZip(sources: Iterable[Path], output: ZipOutputStream, recursive: Boolean, log: Logger)(createEntry: String => ZipEntry) =
	{
		def add(source: Path)
		{
			val sourceFile = source.asFile
			if(sourceFile.isDirectory)
			{
				if(recursive)
					wrapNull(sourceFile.listFiles).foreach(file => add(source / file.getName))
			}
			else if(sourceFile.exists)
			{
				val relativePath = source.relativePathString("/")
				log.debug("\tAdding " + source + " as " + relativePath + " ...")
				val nextEntry = createEntry(relativePath)
				nextEntry.setTime(sourceFile.lastModified)
				output.putNextEntry(nextEntry)
				transferAndClose(new FileInputStream(sourceFile), output, log)
			}
			else
				log.warn("\tSource " + source + " does not exist.")
		}
		sources.foreach(add)
		output.closeEntry()
		None
	}
	
	private def withZipOutput(file: File, manifest: Option[Manifest], log: Logger)(f: ZipOutputStream => Option[String]): Option[String] =
	{
		writeStream(file, log)
		{
			fileOut =>
			{
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
				Control.trapUnitAndFinally("Error writing " + ext + ": ", log)
					{ f(zipOut) } { zipOut.close }
			}
		}
	}
	import scala.collection.Set
	/** Unzips the contents of the zip file <code>from</code> to the <code>toDirectory</code> directory.*/
	def unzip(from: Path, toDirectory: Path, log: Logger): Either[String, Set[Path]] =
		unzip(from, toDirectory, AllPassFilter, log)
	/** Unzips the contents of the zip file <code>from</code> to the <code>toDirectory</code> directory.*/
	def unzip(from: File, toDirectory: Path, log: Logger): Either[String, Set[Path]] =
		unzip(from, toDirectory, AllPassFilter, log)
	/** Unzips the contents of the zip file <code>from</code> to the <code>toDirectory</code> directory.*/
	def unzip(from: InputStream, toDirectory: Path, log: Logger): Either[String, Set[Path]] =
		unzip(from, toDirectory, AllPassFilter, log)
	/** Unzips the contents of the zip file <code>from</code> to the <code>toDirectory</code> directory.*/
	def unzip(from: URL, toDirectory: Path, log: Logger): Either[String, Set[Path]] =
		unzip(from, toDirectory, AllPassFilter, log)
	
	/** Unzips the contents of the zip file <code>from</code> to the <code>toDirectory</code> directory.
	* Only the entries that match the given filter are extracted. */
	def unzip(from: Path, toDirectory: Path, filter: NameFilter, log: Logger): Either[String, Set[Path]] =
		unzip(from.asFile, toDirectory, filter, log)
	/** Unzips the contents of the zip file <code>from</code> to the <code>toDirectory</code> directory.
	* Only the entries that match the given filter are extracted. */
	def unzip(from: File, toDirectory: Path, filter: NameFilter, log: Logger): Either[String, Set[Path]] =
		readStreamValue(from, log)(in => unzip(in, toDirectory, filter, log))
	/** Unzips the contents of the zip file <code>from</code> to the <code>toDirectory</code> directory.
	* Only the entries that match the given filter are extracted. */
	def unzip(from: URL, toDirectory: Path, filter: NameFilter, log: Logger): Either[String, Set[Path]] =
		readStreamValue(from, log) { stream => unzip(stream, toDirectory, filter, log) }
	/** Unzips the contents of the zip file <code>from</code> to the <code>toDirectory</code> directory.
	* Only the entries that match the given filter are extracted. */
	def unzip(from: InputStream, toDirectory: Path, filter: NameFilter, log: Logger): Either[String, Set[Path]] =
	{
		createDirectory(toDirectory, log) match
		{
			case Some(err) => Left(err)
			case None => zipInputStream.io(from, "unzipping", log) { zipInput => extract(zipInput, toDirectory, filter, log) }
		}
	}
	private def extract(from: ZipInputStream, toDirectory: Path, filter: NameFilter, log: Logger) =
	{
		val set = new scala.collection.mutable.HashSet[Path]
		def next(): Option[String] =
		{
			val entry = from.getNextEntry
			if(entry == null)
				None
			else
			{
				val name = entry.getName
				val result =
					if(filter.accept(name))
					{
						val target = Path.fromString(toDirectory, name)
						log.debug("Extracting zip entry '" + name + "' to '" + target + "'")
						val result =
							if(entry.isDirectory)
								createDirectory(target, log)
							else
							{
								set += target
								writeStream(target.asFile, log) { out => FileUtilities.transfer(from, out, log) }
							}
						//target.asFile.setLastModified(entry.getTime)
						result
					}
					else
					{
						log.debug("Ignoring zip entry '" + name + "'")
						None
					}
				from.closeEntry()
				result match { case None => next(); case x => x }
			}
		}
		next().toLeft(readOnly(set))
	}
	
	/** Copies all bytes from the given input stream to the given output stream.
	* Neither stream is closed.*/
	def transfer(in: InputStream, out: OutputStream, log: Logger): Option[String] =
		transferImpl(in, out, false, log)
	/** Copies all bytes from the given input stream to the given output stream.  The
	* input stream is closed after the method completes.*/
	def transferAndClose(in: InputStream, out: OutputStream, log: Logger): Option[String] =
		transferImpl(in, out, true, log)
	private def transferImpl(in: InputStream, out: OutputStream, close: Boolean, log: Logger): Option[String] =
	{
		Control.trapUnitAndFinally("Error during transfer: ", log)
		{
			val buffer = new Array[Byte](BufferSize)
			def read: None.type =
			{
				val byteCount = in.read(buffer)
				if(byteCount >= 0)
				{
					out.write(buffer, 0, byteCount)
					read
				}
				else
					None
			}
			read
		}
		{ if(close) in.close }
	}

	/** Creates a file at the given location.*/
	def touch(path: Path, log: Logger): Option[String] = touch(path.asFile, log)
	/** Creates a file at the given location.*/
	def touch(file: File, log: Logger): Option[String] =
	{
		Control.trapUnit("Could not create file " + file + ": ", log)
		{
			if(file.exists)
			{
				def updateFailBase = "Could not update last modified for file " + file
				Control.trapUnit(updateFailBase + ": ", log)
					{ if(file.setLastModified(System.currentTimeMillis)) None else Some(updateFailBase) }
			}
			else
				createDirectory(file.getParentFile, log) orElse { file.createNewFile(); None }
		}
	}
	/** Creates a directory at the given location.*/
	def createDirectory(dir: Path, log: Logger): Option[String] = createDirectory(dir.asFile, log)
	/** Creates a directory at the given location.*/
	def createDirectory(dir: File, log: Logger): Option[String] =
	{
		Control.trapUnit("Could not create directory " + dir + ": ", log)
		{
			if(dir.exists)
			{
				if(dir.isDirectory)
					None
				else
					Some(dir + " exists and is not a directory.")
			}
			else
			{
				dir.mkdirs()
				log.debug("Created directory " + dir)
				None
			}
		}
	}
	/** Creates directories at the given locations.*/
	def createDirectories(d: Seq[Path], log: Logger): Option[String] = createDirectories(d.toList.map(_.asFile), log)
	/** Creates directories at the given locations.*/
	def createDirectories(d: List[File], log: Logger): Option[String] =
		d match
		{
			case Nil => None
			case head :: tail => createDirectory(head, log) orElse createDirectories(tail, log)
		}
	/** The maximum number of times a unique temporary filename is attempted to be created.*/
	private val MaximumTries = 10
	/** Creates a temporary directory and returns it.*/
	def createTemporaryDirectory(log: Logger): Either[String, File] =
	{
		def create(tries: Int): Either[String, File] =
		{
			if(tries > MaximumTries)
				Left("Could not create temporary directory.")
			else
			{
				val randomName = "sbt_" + java.lang.Integer.toHexString(random.nextInt)
				val f = new File(temporaryDirectory, randomName)
				
				if(createDirectory(f, log).isEmpty)
					Right(f)
				else
					create(tries + 1)
			}
		}
		create(0)
	}

	def withTemporaryDirectory(log: Logger)(action: File => Option[String]): Option[String] =
		 doInTemporaryDirectory(log: Logger)(file => action(file).toLeft(())).left.toOption
	/** Creates a temporary directory and provides its location to the given function.  The directory
	* is deleted after the function returns.*/
	def doInTemporaryDirectory[T](log: Logger)(action: File => Either[String, T]): Either[String, T] =
	{
		def doInDirectory(dir: File): Either[String, T] =
		{
			Control.trapAndFinally("", log)
				{ action(dir) }
				{ delete(dir, true, log) }
		}
		createTemporaryDirectory(log).right.flatMap(doInDirectory)
	}
	def withTemporaryFile[T](log: Logger, prefix: String, postfix: String)(action: File => Either[String, T]): Either[String, T] =
	{
		Control.trap("Error creating temporary file: ", log)
		{
			val file = File.createTempFile(prefix, postfix)
			Control.trapAndFinally("", log)
				{ action(file) }
				{ file.delete() }
		}
	}
	
	/** Copies the files declared in <code>sources</code> to the <code>destinationDirectory</code>
	* directory.  The source directory hierarchy is flattened so that all copies are immediate
	* children of <code>destinationDirectory</code>.  Directories are not recursively entered.*/
	def copyFlat(sources: Iterable[Path], destinationDirectory: Path, log: Logger) =
	{
		val targetSet = new scala.collection.mutable.HashSet[Path]
		copyImpl(sources, destinationDirectory, log)
		{
			source =>
			{
				val from = source.asFile
				val toPath = destinationDirectory / from.getName
				targetSet += toPath
				val to = toPath.asFile
				if(!to.exists || from.lastModified > to.lastModified && !from.isDirectory)
				{
					log.debug("Copying " + source + " to " + toPath)
					copyFile(from, to, log)
				}
				else
					None
			}
		}.toLeft(readOnly(targetSet))
	}
	private def copyImpl(sources: Iterable[Path], destinationDirectory: Path, log: Logger)
		(doCopy: Path => Option[String]): Option[String] =
	{
		val target = destinationDirectory.asFile
		val creationError =
			if(target.isDirectory)
				None
			else
				createDirectory(target, log)
		def copy(sources: List[Path]): Option[String] =
		{
			sources match
			{
				case src :: remaining =>
				{
					doCopy(src) match
					{
						case None => copy(remaining)
						case error => error
					}
				}
				case Nil => None
			}
		}
		creationError orElse ( Control.trapUnit("", log) { copy(sources.toList) } )
	}
	/** Retrieves the content of the given URL and writes it to the given File. */
	def download(url: URL, to: File, log: Logger) =
	{
		readStream(url, log) { inputStream =>
			writeStream(to, log) { outputStream => 
				transfer(inputStream, outputStream, log)
			}
		}
	}
	/** Copies the files declared in <code>sources</code> to the <code>destinationDirectory</code>
	* directory.  Directories are not recursively entered.  The destination hierarchy matches the
	* source paths relative to any base directories.  For example:
	*
	* A source <code>(basePath ##) / x / y</code> is copied to <code>destinationDirectory / x / y</code>.
	* */
	def copy(sources: Iterable[Path], destinationDirectory: Path, log: Logger) =
	{
		val targetSet = new scala.collection.mutable.HashSet[Path]
		copyImpl(sources, destinationDirectory, log)
		{
			source =>
			{
				val from = source.asFile
				val toPath = Path.fromString(destinationDirectory, source.relativePath)
				targetSet += toPath
				val to = toPath.asFile
				if(!to.exists || from.lastModified > to.lastModified)
				{
					if(from.isDirectory)
						createDirectory(to, log)
					else
					{
						log.debug("Copying " + source + " to " + toPath)
						copyFile(from, to, log)
					}
				}
				else
					None
			}
		}.toLeft(readOnly(targetSet))
	}
	
	/** Copies the files declared in <code>sources</code> to the <code>targetDirectory</code>
	* directory.  The source directory hierarchy is flattened so that all copies are immediate
	* children of <code>targetDirectory</code>.  Directories are not recursively entered.*/
	def copyFilesFlat(sources: Iterable[File], targetDirectory: Path, log: Logger) =
	{
		require(targetDirectory.asFile.isDirectory, "Target '" + targetDirectory + "' is not a directory.")
		val byName = new scala.collection.mutable.HashMap[String, File]
		for(source <- sources) byName.put(source.getName, source)
		val uniquelyNamedSources = byName.values
		val targetSet = new scala.collection.mutable.HashSet[Path]
		def copy(source: File): Option[String] =
		{
			if(source.isDirectory)
				copyAll(source.listFiles.toList)
			else if(source.exists)
			{
				val targetPath = targetDirectory / source.getName
				targetSet += targetPath
				if(!targetPath.exists || source.lastModified > targetPath.lastModified)
				{
					log.debug("Copying " + source + " to " + targetPath)
					copyFile(source, targetPath.asFile, log)
				}
				else
					None
			}
			else
				None
		}
		def copyAll(sources: List[File]): Option[String] =
			sources match
			{
				case head :: tail =>
					copy(head) match
					{
						case None => copyAll(tail)
						case x => x
					}
				case Nil => None
			}
		
		Control.trap("Error copying files: ", log) { copyAll(uniquelyNamedSources.toList).toLeft(readOnly(targetSet)) }
	}
	/** Copies <code>sourceFile</code> to <code>targetFile</code>.  If <code>targetFile</code>
	* exists, it is overwritten.  Note that unlike higher level copies in FileUtilities, this
	* method always performs the copy, even if sourceFile is older than targetFile.*/
	def copyFile(sourceFile: Path, targetFile: Path, log: Logger): Option[String] =
		copyFile(sourceFile.asFile, targetFile.asFile, log)
	/** Copies <code>sourceFile</code> to <code>targetFile</code>.  If <code>targetFile</code>
	* exists, it is overwritten.  Note that unlike higher level copies in FileUtilities, this
	* method always performs the copy, even if sourceFile is older than targetFile.*/
	def copyFile(sourceFile: File, targetFile: File, log: Logger): Option[String] =
	{
		require(sourceFile.exists, "Source file '" + sourceFile.getAbsolutePath + "' does not exist.")
		require(!sourceFile.isDirectory, "Source file '" + sourceFile.getAbsolutePath + "' is a directory.")
		readChannel(sourceFile, log)(
			in => writeChannel(targetFile, log) {
				out => {
					val copied = out.transferFrom(in, 0, in.size)
					if(copied == in.size)
						None
					else
						Some("Could not copy '" + sourceFile + "' to '" + targetFile + "' (" + copied + "/" + in.size + " bytes copied)")
				}
			}
		)
	}
	
	/** Synchronizes the contents of the <code>sourceDirectory</code> directory to the
	* <code>targetDirectory</code> directory.*/
	def sync(sourceDirectory: Path, targetDirectory: Path, log: Logger): Option[String] =
	{
		copy(((sourceDirectory ##) ** AllPassFilter).get, targetDirectory, log).right.flatMap
			{ copiedTo => prune(targetDirectory, copiedTo, log).toLeft(()) }.left.toOption
	}
	def prune(directory: Path, keepOnly: Iterable[Path], log: Logger): Option[String] =
	{
		val existing = ((directory ##) ** AllPassFilter).get
		val toRemove = scala.collection.mutable.HashSet(existing.toSeq: _*)
		toRemove --= keepOnly
		if(log.atLevel(Level.Debug))
			toRemove.foreach(r => log.debug("Pruning " + r))
		clean(toRemove, true, log)
	}
	
	/** Copies the contents of the <code>source</code> directory to the <code>target</code> directory .*/
	def copyDirectory(source: Path, target: Path, log: Logger): Option[String] =
		copyDirectory(source.asFile, target.asFile, log)
	/** Copies the contents of the <code>source</code> directory to the <code>target</code> directory .*/
	def copyDirectory(source: File, target: File, log: Logger): Option[String] =
	{
		require(source.isDirectory, "Source '" + source.getAbsolutePath + "' is not a directory.")
		require(!target.exists, "Target '" + target.getAbsolutePath + "' already exists.")
		def copyDirectory(sourceDir: File, targetDir: File): Option[String] =
			createDirectory(targetDir, log) orElse copyContents(sourceDir, targetDir)
		def copyContents(sourceDir: File, targetDir: File): Option[String] =
			sourceDir.listFiles.foldLeft(None: Option[String])
			{
				(result, file) =>
					result orElse
					{
						val targetFile = new File(targetDir, file.getName)
						if(file.isDirectory)
							copyDirectory(file, targetFile)
						else
							copyFile(file, targetFile, log)
					}
			}
		copyDirectory(source, target)
	}

	
	/** Deletes the given file recursively.*/
	def clean(file: Path, log: Logger): Option[String] = clean(file :: Nil, log)
	/** Deletes the given files recursively.*/
	def clean(files: Iterable[Path], log: Logger): Option[String] = clean(files, false, log)
	/** Deletes the given files recursively.  <code>quiet</code> determines the logging level.
	* If it is true, each file in <code>files</code> is logged at the <code>info</code> level.
	* If it is false, the <code>debug</code> level is used.*/
	def clean(files: Iterable[Path], quiet: Boolean, log: Logger): Option[String] =
		deleteFiles(files.map(_.asFile), quiet, log)
			
	private def deleteFiles(files: Iterable[File], quiet: Boolean, log: Logger): Option[String] =
		((None: Option[String]) /: files)( (result, file) => result orElse delete(file, quiet, log))
	private def delete(file: File, quiet: Boolean, log: Logger): Option[String] =
	{
		def logMessage(message: => String)
		{
			log.log(if(quiet) Level.Debug else Level.Info, message)
		}
		Control.trapUnit("Error deleting file " + file + ": ", log)
		{
			if(file.isDirectory)
			{
				logMessage("Deleting directory " + file)
				deleteFiles(wrapNull(file.listFiles), true, log)
				file.delete
			}
			else if(file.exists)
			{
				logMessage("Deleting file " + file)
				file.delete
			}
			None
		}
	}
	
	/** Appends the given <code>String content</code> to the provided <code>file</code> using the default encoding.
	* A new file is created if it does not exist.*/
	def append(file: File, content: String, log: Logger): Option[String] = append(file, content, Charset.defaultCharset, log)
	/** Appends the given <code>String content</code> to the provided <code>file</code> using the given encoding.
	* A new file is created if it does not exist.*/
	def append(file: File, content: String, charset: Charset, log: Logger): Option[String] =
		write(file, content, charset, true, log)
	
	/** Writes the given <code>String content</code> to the provided <code>file</code> using the default encoding.
	* If the file exists, it is overwritten.*/
	def write(file: File, content: String, log: Logger): Option[String] = write(file, content, Charset.defaultCharset, log)
	/** Writes the given <code>String content</code> to the provided <code>file</code> using the given encoding.
	* If the file already exists, it is overwritten.*/
	def write(file: File, content: String, charset: Charset, log: Logger): Option[String] =
		write(file, content, charset, false, log)
	private def write(file: File, content: String, charset: Charset, append: Boolean, log: Logger): Option[String] =
	{
		if(charset.newEncoder.canEncode(content))
			write(file, charset, append, log) { w =>  w.write(content); None }
		else
			Some("String cannot be encoded by default charset.")
	}
	
	/** Opens a <code>Writer</code> on the given file using the default encoding,
	* passes it to the provided function, and closes the <code>Writer</code>.*/
	def write(file: File, log: Logger)(f: Writer => Option[String]): Option[String] =
		write(file, Charset.defaultCharset, log)(f)
	/** Opens a <code>Writer</code> on the given file using the given encoding,
	* passes it to the provided function, and closes the <code>Writer</code>.*/
	def write(file: File, charset: Charset, log: Logger)(f: Writer => Option[String]): Option[String] =
		write(file, charset, false, log)(f)
	private def write(file: File, charset: Charset, append: Boolean, log: Logger)(f: Writer => Option[String]): Option[String] =
		fileWriter(charset, append).ioOption(file, Writing, log)(f)
		
	/** Opens a <code>Reader</code> on the given file using the default encoding,
	* passes it to the provided function, and closes the <code>Reader</code>.*/
	def read(file: File, log: Logger)(f: Reader => Option[String]): Option[String] =
		read(file, Charset.defaultCharset, log)(f)
	/** Opens a <code>Reader</code> on the given file using the default encoding,
	* passes it to the provided function, and closes the <code>Reader</code>.*/
	def read(file: File, charset: Charset, log: Logger)(f: Reader => Option[String]): Option[String] =
		fileReader(charset).ioOption(file, Reading, log)(f)
	/** Opens a <code>Reader</code> on the given file using the default encoding,
	* passes it to the provided function, and closes the <code>Reader</code>.*/
	def readValue[R](file: File, log: Logger)(f: Reader => Either[String, R]): Either[String, R] =
		readValue(file, Charset.defaultCharset, log)(f)
	/** Opens a <code>Reader</code> on the given file using the given encoding,
	* passes it to the provided function, and closes the <code>Reader</code>.*/
	def readValue[R](file: File, charset: Charset, log: Logger)(f: Reader => Either[String, R]): Either[String, R] =
		fileReader(charset).io(file, Reading, log)(f)
		
	/** Reads the contents of the given file into a <code>String</code> using the default encoding.
	*  The resulting <code>String</code> is wrapped in <code>Right</code>.*/
	def readString(file: File, log: Logger): Either[String, String] = readString(file, Charset.defaultCharset, log)
	/** Reads the contents of the given file into a <code>String</code> using the given encoding.
	*  The resulting <code>String</code> is wrapped in <code>Right</code>.*/
	def readString(file: File, charset: Charset, log: Logger): Either[String, String] = readValue(file, charset, log)(readString)
	
	def readString(in: InputStream, log: Logger): Either[String, String] = readString(in, Charset.defaultCharset, log)
	def readString(in: InputStream, charset: Charset, log: Logger): Either[String, String] =
		streamReader.io((in, charset), Reading, log)(readString)
	def readString(in: Reader, log: Logger): Either[String, String] =
		Control.trapAndFinally("Error reading bytes from reader: ", log)
			{ readString(in) }
			{ in.close() }
	private def readString(in: Reader): Either[String, String] =
	{
		val builder = new StringBuilder
		val buffer = new Array[Char](BufferSize)
		def readNext()
		{
			val read = in.read(buffer, 0, buffer.length)
			if(read >= 0)
			{
				builder.append(buffer, 0, read)
				readNext()
			}
			else
				None
		}
		readNext()
		Right(builder.toString)
	}
	/** Appends the given bytes to the given file. */
	def append(file: File, bytes: Array[Byte], log: Logger): Option[String] =
		writeBytes(file, bytes, true, log)
	/** Writes the given bytes to the given file. If the file already exists, it is overwritten.*/
	def write(file: File, bytes: Array[Byte], log: Logger): Option[String] =
		writeBytes(file, bytes, false, log)
	private def writeBytes(file: File, bytes: Array[Byte], append: Boolean, log: Logger): Option[String] =
		writeStream(file, append, log) { out => out.write(bytes); None }
	
	/** Reads the entire file into a byte array. */
	def readBytes(file: File, log: Logger): Either[String, Array[Byte]] = readStreamValue(file, log)(readBytes)
	def readBytes(in: InputStream, log: Logger): Either[String, Array[Byte]] =
		Control.trapAndFinally("Error reading bytes from input stream: ", log)
			{ readBytes(in) }
			{ in.close() }
	private def readBytes(in: InputStream): Either[String, Array[Byte]] =
	{
		val out = new ByteArrayOutputStream
		val buffer = new Array[Byte](BufferSize)
		def readNext()
		{
			val read = in.read(buffer)
			if(read >= 0)
			{
				out.write(buffer, 0, read)
				readNext()
			}
		}
		readNext()
		Right(out.toByteArray)
	}
		
	/** Opens an <code>OutputStream</code> on the given file with append=true and passes the stream
	* to the provided function.  The stream is closed before this function returns.*/
	def appendStream(file: File, log: Logger)(f: OutputStream => Option[String]): Option[String] =
		fileOutputStream(true).ioOption(file, Appending, log)(f)
	/** Opens an <code>OutputStream</code> on the given file and passes the stream
	* to the provided function.  The stream is closed before this function returns.*/
	def writeStream(file: File, log: Logger)(f: OutputStream => Option[String]): Option[String] =
		fileOutputStream(false).ioOption(file, Writing, log)(f)
	private def writeStream(file: File, append: Boolean, log: Logger)(f: OutputStream => Option[String]): Option[String] =
		if(append) appendStream(file, log)(f) else writeStream(file, log)(f)
	/** Opens an <code>InputStream</code> on the given file and passes the stream
	* to the provided function.  The stream is closed before this function returns.*/
	def readStream(file: File, log: Logger)(f: InputStream => Option[String]): Option[String] =
		fileInputStream.ioOption(file, Reading, log)(f)
	/** Opens an <code>InputStream</code> on the given file and passes the stream
	* to the provided function.  The stream is closed before this function returns.*/
	def readStreamValue[R](file: File, log: Logger)(f: InputStream => Either[String, R]): Either[String, R] =
		fileInputStream.io(file, Reading, log)(f)
	/** Opens an <code>InputStream</code> on the given <code>URL</code> and passes the stream
	* to the provided function.  The stream is closed before this function returns.*/
	def readStream(url: URL, log: Logger)(f: InputStream => Option[String]): Option[String] =
		urlInputStream.ioOption(url, Reading, log)(f)
	/** Opens an <code>InputStream</code> on the given <code>URL</code> and passes the stream
	* to the provided function.  The stream is closed before this function returns.*/
	def readStreamValue[R](url: URL, log: Logger)(f: InputStream => Either[String, R]): Either[String, R] =
		urlInputStream.io(url, Reading, log)(f)
		
	/** Opens a <code>FileChannel</code> on the given file for writing and passes the channel
	* to the given function.  The channel is closed before this function returns.*/
	def writeChannel(file: File, log: Logger)(f: FileChannel => Option[String]): Option[String] =
		fileOutputChannel.ioOption(file, Writing, log)(f)
	/** Opens a <code>FileChannel</code> on the given file for reading and passes the channel
	* to the given function.  The channel is closed before this function returns.*/
	def readChannel(file: File, log: Logger)(f: FileChannel => Option[String]): Option[String] =
		fileInputChannel.ioOption(file, Reading, log)(f)
	/** Opens a <code>FileChannel</code> on the given file for reading and passes the channel
	* to the given function.  The channel is closed before this function returns.*/
	def readChannelValue[R](file: File, log: Logger)(f: FileChannel => Either[String, R]): Either[String, R] =
		fileInputChannel.io(file, Reading, log)(f)
	
	private[sbt] def wrapNull(a: Array[File]): Array[File] =
		if(a == null)
			new Array[File](0)
		else
			a
			
	/** Writes the given string to the writer followed by a newline.*/
	private[sbt] def writeLine(writer: Writer, line: String)
	{
		writer.write(line)
		writer.write(Newline)
	}
	
	/** The directory in which temporary files are placed.*/
	val temporaryDirectory = new File(System.getProperty("java.io.tmpdir"))
	def classLocation(cl: Class[_]): URL = cl.getProtectionDomain.getCodeSource.getLocation
	def classLocationFile(cl: Class[_]): File = new File(classLocation(cl).toURI)
	def classLocation[T](implicit mf: scala.reflect.Manifest[T]): URL = classLocation(mf.erasure)
	def classLocationFile[T](implicit mf: scala.reflect.Manifest[T]): File = classLocationFile(mf.erasure)
	
	/** The location of the jar containing this class.*/
	lazy val sbtJar: File = classLocationFile(getClass)
	lazy val scalaLibraryJar: File = classLocationFile[scala.ScalaObject]
	lazy val scalaCompilerJar: File = classLocationFile[scala.tools.nsc.Settings]
	
	/** The producer of randomness for unique name generation.*/
	private val random = new java.util.Random

	private val Reading = "reading"
	private val Writing = "writing"
	private val Appending = "appending"
}

private abstract class OpenResource[Source, T] extends NotNull
{
	import OpenResource.{unwrapEither, wrapEither}
	protected def open(src: Source, log: Logger): Either[String, T]
	def ioOption(src: Source, op: String, log: Logger)(f: T => Option[String]) =
		unwrapEither( io(src, op, log)(wrapEither(f)) )
	def io[R](src: Source, op: String, log: Logger)(f: T => Either[String,R]): Either[String, R] =
		open(src, log).right flatMap
		{
			resource => Control.trapAndFinally("Error " + op + " "+ src + ": ", log)
				{ f(resource) }
				{ close(resource) }
		}
	protected def close(out: T): Unit
}
private trait CloseableOpenResource[Source, T <: Closeable] extends OpenResource[Source, T]
{
	protected def close(out: T): Unit = out.close()
}
import scala.reflect.{Manifest => SManifest}
private abstract class WrapOpenResource[Source, T <: Closeable](implicit srcMf: SManifest[Source], targetMf: SManifest[T]) extends CloseableOpenResource[Source, T]
{
	private def label[S](m: SManifest[S]) = m.erasure.getSimpleName
	protected def open(source: Source): T
	protected final def open(source: Source, log: Logger): Either[String, T] =
		Control.trap("Error wrapping " + label(srcMf) + " in " + label(targetMf) + ": ", log) { Right(open(source)) }
}
private abstract class OpenFile[T] extends OpenResource[File, T]
{
	protected def open(file: File): T
	protected final def open(file: File, log: Logger): Either[String, T] =
	{
		val parent = file.getParentFile
		if(parent != null)
			FileUtilities.createDirectory(parent, log)
		Control.trap("Error opening " + file + ": ", log) { Right(open(file)) }
	}
}
private abstract class CloseableOpenFile[T <: Closeable] extends OpenFile[T] with CloseableOpenResource[File, T]
private object OpenResource
{
	private def wrapEither[R](f: R => Option[String]): (R => Either[String, Unit]) = (r: R) => f(r).toLeft(())
	private def unwrapEither(e: Either[String, Unit]): Option[String] = e.left.toOption
	
	def fileOutputStream(append: Boolean) =
		new CloseableOpenFile[FileOutputStream] { protected def open(file: File) = new FileOutputStream(file, append) }
	def fileInputStream = new CloseableOpenFile[FileInputStream]
		{ protected def open(file: File) = new FileInputStream(file) }
	def urlInputStream = new CloseableOpenResource[URL, InputStream]
		{ protected def open(url: URL, log: Logger) = Control.trap("Error opening " + url + ": ", log) { Right(url.openStream) } }
	def fileOutputChannel = new CloseableOpenFile[FileChannel]
		{ protected def open(f: File) = (new FileOutputStream(f)).getChannel }
	def fileInputChannel = new CloseableOpenFile[FileChannel]
		{ protected def open(f: File) = (new FileInputStream(f)).getChannel }
	def fileWriter(charset: Charset, append: Boolean) = new CloseableOpenFile[Writer]
		{ protected def open(f: File) = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, append), charset)) }
	def fileReader(charset: Charset) = new CloseableOpenFile[Reader]
		{ protected def open(f: File) = new BufferedReader(new InputStreamReader(new FileInputStream(f), charset)) }
	def jarFile(verify: Boolean) = new OpenFile[JarFile]
		{ protected def open(f: File) = new JarFile(f, verify)
		   override protected def close(j: JarFile) = j.close() }
	def zipFile = new OpenFile[ZipFile]
		{ protected def open(f: File) = new ZipFile(f)
		   override protected def close(z: ZipFile) = z.close() }
	def streamReader = new WrapOpenResource[(InputStream, Charset), Reader]
		{ protected def open(streamCharset: (InputStream, Charset)) = new InputStreamReader(streamCharset._1, streamCharset._2) }
	def gzipInputStream = new WrapOpenResource[InputStream, GZIPInputStream]
		{ protected def open(in: InputStream) = new GZIPInputStream(in) }
	def zipInputStream = new WrapOpenResource[InputStream, ZipInputStream]
		{ protected def open(in: InputStream) = new ZipInputStream(in) }
	def gzipOutputStream = new WrapOpenResource[OutputStream, GZIPOutputStream]
		{ protected def open(out: OutputStream) = new GZIPOutputStream(out)
		   override protected def close(out: GZIPOutputStream) = out.finish() }
	def jarOutputStream = new WrapOpenResource[OutputStream, JarOutputStream]
		{ protected def open(out: OutputStream) = new JarOutputStream(out) }
	def jarInputStream = new WrapOpenResource[InputStream, JarInputStream]
		{ protected def open(in: InputStream) = new JarInputStream(in) }
	def zipEntry(zip: ZipFile) = new CloseableOpenResource[ZipEntry, InputStream] {
		protected def open(entry: ZipEntry, log: Logger) =
			Control.trap("Error opening " + entry.getName + " in " + zip + ": ", log) { Right(zip.getInputStream(entry)) }
	}
}