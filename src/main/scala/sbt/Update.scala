/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

/** A trait that provides a task for updating sbt.  */
trait UpdateSbt extends Project
{
	/** The first argument is the version to update to and is mandatory.
	* The second argument is the location of the launcher jar.  If omitted, the launcher used to launch the currently running instance of sbt is used.*/
	lazy val sbtUpdate = task { args => task { (new Update(this))(args) } } describedAs("Updates the version of sbt used to build this project and updates the launcher jar.")
}

import java.io.{File, InputStream, IOException}
import java.net.{HttpURLConnection, URL}
import HttpURLConnection.{HTTP_NOT_FOUND , HTTP_OK}
import SimpleReader.readLine
import xsbt.FileUtilities.{classLocationFile, copyFile, readLines, transfer, unzip, withTemporaryDirectory, write, zip}
import xsbt.PathMapper.relativeTo
import xsbt.Paths._
import xsbt.OpenResource.{fileOutputStream, urlInputStream}

private class Update(project: Project)
{
	val info = project.info
	val app = info.app
	val log = project.log
	
	/** The location of the jar used to launch the currently running instance of sbt.*/
	lazy val launcherJar = classLocationFile[xsbti.AppProvider]
	/** A temporary jar file to use in the given directory. */
	def tempJar(dir: File) = dir / launcherJar.getName
		
	/** Implementation of the sbt-update task: reads arguments and hands off to the other `apply`.*/
	def apply(args: Array[String]): Option[String] =
		args match
		{
			case Array(version) if validVersion(version) => apply(version, None)
			case Array(version, temporaryJar) if validVersion(version) => apply(version, Some(new File(temporaryJar) getAbsoluteFile))
			case _ => Some("Expected '<version>' or '<version> <new-launcher-file>', got '" + args.mkString(" ") + "'")
		}

	def validVersion(version: String) = !version.trim.isEmpty

	/** Implementation of the sbt-update task after arguments have checked.  Gives user a chance to cancel and continues with `doUpdate`.*/
	def apply(version: String, temporaryJar: Option[File]): Option[String] =
	{
		readLine("Updating the sbt version requires a restart.  Continue? (Y/n) ") match
		{
			case Some(line) if(isYes(line)) => doUpdate(version, temporaryJar)
			case _ => Some("Update declined.")
		}
	}
	/** Implementation of the sbt-update task: high-level control after initial verification.*/
	def doUpdate(version: String, temporaryJar: Option[File]): Option[String] =
	{
		retrieveNewVersion(version)
		log.info("Version is valid.  Setting 'sbt.version' to " + version + "...")
		setNewVersion(version)

		log.info("'sbt.version' updated.")
		if(temporaryJar.isDefined || updateInPlace(version))
		{
			log.info("Downloading new launcher ...")
			
			if(downloadLauncher(version, temporaryJar))
				log.info("Downloaded launcher.")
			else
				tryUpdateLauncher(version, temporaryJar)
		}
		else
			log.info("Launcher update declined.")

		log.info("Please restart sbt.")
		System.exit(0)
		None
	}
	/** Updates 'sbt.version' in `project/build.properties`.*/
	def setNewVersion(version: String)
	{
		project.sbtVersion() = version
		project.saveEnvironment()
	}
	/** Retrieves the given `version` of sbt in order to verify the version is valid.*/
	def retrieveNewVersion(version: String)
	{
		val newAppID = changeVersion(app.id, version)
		log.info("Checking repositories for sbt " + version + " ...")
		app.scalaProvider.app(newAppID)
	}
	/** Asks the user whether the current launcher should be overrwritten.  Called when no file is explicitly specified as an argument. */
	def updateInPlace(version: String) =
	{
		val input = readLine(" The current launcher (" + launcherJar + ") will be updated in place.  Continue? (Y/n) ")
		isYes(input)
	}
	def isYes(line: Option[String]): Boolean = line.filter(isYes).isDefined
	
	/** Updates the launcher as in `updateLauncher` but performs various checks and logging around it. */
	def tryUpdateLauncher(version: String, temporaryJar: Option[File])
	{
		log.warn("No launcher found for '" + version + "'")
		def promptStart = if(temporaryJar.isDefined) " Copy current launcher but with " else " Modify current launcher to use "
		val input = readLine(promptStart + version + " as the default for new projects? (Y/n) ")
		val updated = isYes(input)
		if(updated) updateLauncher(version, temporaryJar)
		
		def extra =  if(temporaryJar.isDefined) " at " + temporaryJar.get + "." else "."
		log.info(if(updated) "Launcher updated" + extra else "Launcher not updated.")
	}
	/** The jar to copy/download to.  If `userJar` is not defined, it is a temporary file in `tmpDir` that should then be moved to the current launcher file.
	* If it is defined and is a directory, the jar is defined in that directory.  If it is a file, that file is returned. */
	def targetJar(tmpDir: File, userJar: Option[File]): File = 
		userJar match { case Some(file) => if(file.isDirectory) tempJar(file) else file; case None => tempJar(tmpDir) }
		
	/** Gets the given `version` of the launcher from Google Code.  If `userProvidedJar` is defined,
	* this updated launcher is downloaded there, otherwise it overwrites the current launcher. */
	def downloadLauncher(version: String, userProvidedJar: Option[File]): Boolean =
	{
		def getLauncher(tmp: File): Boolean =
		{
			val temporaryJar = targetJar(tmp, userProvidedJar)
			temporaryJar.getParentFile.mkdirs()
			val url = launcherURL(version)
			val connection = url.openConnection.asInstanceOf[HttpURLConnection]
			connection.setInstanceFollowRedirects(false)
			
			def download(in: InputStream): Unit = fileOutputStream(false)(temporaryJar) { out => transfer(in, out) }
			def checkAndRetrieve(in: InputStream): Boolean =  (connection.getResponseCode == HTTP_OK) && { download(in); true }
			def handleError(e: IOException) = if(connection.getResponseCode == HTTP_NOT_FOUND ) false else throw e
			def retrieve() =
			{
				val in = connection.getInputStream
				try { checkAndRetrieve(in) } finally { in.close() }
			}
			
			val success = try { retrieve() } catch { case e: IOException => handleError(e)} finally { connection.disconnect() }
			if(success && userProvidedJar.isEmpty)
				move(temporaryJar, launcherJar)
			success
		}
		withTemporaryDirectory(getLauncher)
	}
	/** The location of the launcher for the given version, if it exists. */
	def launcherURL(version: String): URL =
		new URL("http://simple-build-tool.googlecode.com/files/sbt-launch-" + version + ".jar")
		
	/** True iff the given user input is empty, 'y' or 'yes' (case-insensitive).*/
	def isYes(line: String) =
	{
		val lower = line.toLowerCase
		lower.isEmpty || lower == "y" || lower == "yes"
	}
	/** Copies the current launcher but with the default 'sbt.version' set to `version`.  If `userProvidedJar` is defined,
	* the updated launcher is copied there, otherwise the copy overwrites the current  launcher. */
	def updateLauncher(version: String, userProvidedJar: Option[File])
	{
		def makeUpdated(base: File, newJar: File)
		{
			val files = unzip(launcherJar, base)
			updateBootProperties(files, version)
			zip(relativeTo(base)( files ), newJar)
		}
		def updateLauncher(tmp: File)
		{
			val basePath = tmp / "launcher-jar"
			val temporaryJar = targetJar(tmp, userProvidedJar)
			makeUpdated(basePath, temporaryJar)
			if(userProvidedJar.isEmpty)
				move(temporaryJar, launcherJar)
		}

		withTemporaryDirectory(updateLauncher)
	}
	
	/** Copies the `src` file to the `dest` file, preferably by renaming.  The `src` file may or may not be removed.*/
	def move(src: File, dest: File)
	{
		val renameSuccess = src renameTo dest
		if(!renameSuccess)
			copyFile(src, dest)
	}
	
	/** Updates the default value used for 'sbt.version' in the 'sbt.boot.properties' file in the launcher `files` to be `version`.*/
	def updateBootProperties(files: Iterable[File], version: String): Unit =
		files.find(_.getName == "sbt.boot.properties").foreach(updateBootProperties(version))
	/** Updates the default value used for 'sbt.version' in the given `file` to be `version`.*/
	def updateBootProperties(version: String)(file: File)
	{
		val newContent = readLines(file) map updateSbtVersion(version)
		write(file, newContent.mkString("\n"))
	}
	
	/** If the given `line` is the 'sbt.version' configuration, update it to use the `newVersion`.*/
	def updateSbtVersion(newVersion: String)(line: String) =
		if(line.trim.startsWith("sbt.version")) sbtVersion(newVersion) else line
		
	/** The configuration line that defines the 'sbt.version' property, using the provided `version` for defaults.*/
	def sbtVersion(version: String) =
		" sbt.version: quick=set(" + version + "), new=prompt(sbt version)[" + version + "], fill=prompt(sbt version)[" + version + "]"
	
	/** Copies the given ApplicationID but with the specified version.*/
	def changeVersion(appID: xsbti.ApplicationID, versionA: String): xsbti.ApplicationID =
		new xsbti.ApplicationID {
			def groupID = appID.groupID
			def name = appID.name
			def version = versionA
			def mainClass = appID.mainClass
			def mainComponents = appID.mainComponents
			def crossVersioned = appID.crossVersioned
			def classpathExtra = appID.classpathExtra
		}
}
