/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package sbt.extract

import java.io.{File, InputStream}
import java.util.zip.{ZipEntry, ZipFile}

object Main
{
	lazy val log: Logger = new ConsoleLogger
	
	def main(args: Array[String])
	{
		if(args.contains("debug"))
			log.setLevel(Level.Debug)
		val result = OpenResource.zipFile.ioOption(FileUtilities.classLocationFile[Install], "processing", log)(process)
		for(msg <- result)
		{
			log.error(msg)
			System.exit(1)
		}
	}
	private[this] val packedGzip = ".pack.gz"
	private def isArchive(name: String) = name.endsWith(".gz") || name.endsWith(".zip")
	private def process(zip: ZipFile) =
	{
		val installEntry = zip.getEntry("install")
		if(installEntry == null)
			Some("Install commands not found.")
		else
		{
			val jarAndZip = wrap.Wrappers.toList(zip.entries).filter(entry => isArchive(entry.getName)).partition(_.getName.endsWith(packedGzip))
			jarAndZip match
			{
				case (Nil, _)=> Some("sbt loader not found.")
				case (_, Nil) => Some("Project to extract and build not found.")
				case (loaderEntry :: _, projectEntry :: _) => extractAndRun(zip, loaderEntry, projectEntry, installEntry)
			}
		}
	}
	private def extractAndRun(zip: ZipFile, loaderEntry: ZipEntry, projectEntry: ZipEntry, installEntry: ZipEntry) =
	{
		val zipResource = OpenResource.zipEntry(zip)
		
		import FileUtilities.{gunzip, readString, transfer, unzip, writeStream}
		val directory = new File(".", trimExtension(projectEntry.getName, ".zip"))
		assume(!directory.exists, "Could not extract project: directory " + projectEntry.getName + " exists.")
		
		val loaderBaseName = trimExtension(loaderEntry.getName, packedGzip)
		val loaderFile = new File(directory, loaderBaseName + ".jar")
		val tempLoaderFile = new File(directory, loaderBaseName + ".pack")
		
		def extractLoader() =
		{
			implicit def fileToPath(f: File) = Path.fromFile(f)
			val result =
				writeStream(tempLoaderFile, log) { out => zipResource.ioOption(loaderEntry, "reading", log)(gunzip(_, out, log)) } orElse
				Pack.unpack(tempLoaderFile, loaderFile, log)
			FileUtilities.clean(tempLoaderFile :: Nil, true, log)
			result.toLeft(loaderFile)
		}

		Control.thread(zipResource.io(installEntry, "reading", log)(readString(_, log))) { installString =>
			Control.thread(parseInstall(installString)) { install =>
				zipResource.io(projectEntry, "reading", log)(unzip(_, Path.fromFile(directory), log)).left.toOption orElse
				Control.thread(extractLoader()) { loaderFile =>
					run(loaderFile, directory, install)
				}
			}
		}
	}
	private def parseInstall(installString: String): Either[String, Install] =
	{
		installString.split(separator) match
		{
			case Array(allOptions, allActions) =>
				val options = allOptions.split("""\n""").toList
				val actions = allActions.split("""\n""").toList
				Right( Install(options, actions) )
			case _ => Left("Invalid install script (no separator found)")
		}
	}
	private def filterEmpty(list: List[String]) = list.filter(!_.isEmpty)
	private def run(loader: File, project: File, install: Install) =
	{
		val command = "java" :: "-cp" :: loader.getAbsolutePath :: filterEmpty(install.options) ::: "sbt.boot.Boot" :: filterEmpty(install.actions)
		val builder = new java.lang.ProcessBuilder(command.toArray : _*)
		val exitCode = (Process(builder) !<)
		if(exitCode == 0)
			None
		else
			Some("sbt exited with nonzero exit code: " + exitCode)
	}
	private def trimExtension(name: String, ext: String) =
	{
		if(name.endsWith(ext))
			name.substring(0, name.length - ext.length)
		else
			name
	}
	// keep this in sync with sbt.extract.SelfExtractingProject
	private def separator = "===================="
}
private final case class Install(options: List[String], actions: List[String]) extends NotNull