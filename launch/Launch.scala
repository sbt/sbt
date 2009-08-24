/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
 package xsbt.boot

// This is the main class for the sbt launcher.  It reads the project/build.properties file to determine the
// version of Scala and sbt requested for the project definition.  It downloads the requested version of
// Scala, sbt, and dependencies to the project's 'project/boot' directory.  It loads the main sbt and then
// satisfies requests from the main sbt for different versions of Scala for use in the build.

import java.io.{File, FileFilter}
import java.net.URLClassLoader

import xsbti.{Exit => IExit, Launcher, MainResult, Reboot, SbtConfiguration, SbtMain}

// contains constants and paths
import BootConfiguration._
import UpdateTarget.{UpdateScala, UpdateSbt}


class Launch(projectRootDirectory: File, mainClassName: String) extends Launcher with NotNull
{
	def this(projectRootDirectory: File) = this(projectRootDirectory, SbtMainClass)
	def this() = this(new File("."))

	import Launch._
	final def boot(args: Array[String])
	{
		checkAndLoad(args) match
		{
			case e: Exit => System.exit(e.code)
			case r: Reboot => boot(r.arguments())
		}
	}
	def checkAndLoad(args: Array[String]): MainResult =
	{
		 // prompt to create project if it doesn't exist.
		checkProject() match
		{
			case Some(result) => result
			case None => load(args)
		}
	}
	/** Loads the project in the current working directory using the version of scala and sbt
	* declared in the build. The class loader used prevents the Scala and Ivy classes used by
	* this loader from being seen by the loaded sbt/project.*/
	def load(args: Array[String]): MainResult =
	{
		val (scalaVersion, sbtVersion) = ProjectProperties.forcePrompt(PropertiesFile)//, forcePrompt : _*)
		load(args, sbtVersion, mainClassName, scalaVersion)
	}
	def componentLocation(sbtVersion: String, id: String, scalaVersion: String): File = new File(getSbtHome(sbtVersion, scalaVersion), id)
	def getSbtHome(sbtVersion: String, scalaVersion: String): File =
	{
		val baseDirectory = new File(BootDirectory, baseDirectoryName(scalaVersion))
		new File(baseDirectory, sbtDirectoryName(sbtVersion))
	}
	def getScalaHome(scalaVersion: String) = new File(new File(BootDirectory, baseDirectoryName(scalaVersion)), ScalaDirectoryName)

	def load(args: Array[String], useSbtVersion: String, mainClassName: String, definitionScalaVersion: String): MainResult =
	{
		val sbtLoader = update(definitionScalaVersion, useSbtVersion)
		val configuration = new SbtConfiguration
		{
			def arguments = args
			def scalaVersion = definitionScalaVersion
			def sbtVersion = useSbtVersion
			def launcher: Launcher = Launch.this
		}
		run(sbtLoader, mainClassName, configuration)
	 }
	def update(scalaVersion: String, sbtVersion: String): ClassLoader =
	{
		val scalaLoader = getScalaLoader(scalaVersion)
		createSbtLoader(sbtVersion, scalaVersion, scalaLoader)
	}
	
	def run(sbtLoader: ClassLoader, mainClassName: String, configuration: SbtConfiguration): MainResult =
	{
		val sbtMain = Class.forName(mainClassName, true, sbtLoader)
		val main = sbtMain.newInstance.asInstanceOf[SbtMain]
		main.run(configuration)
	}

	final val ProjectDirectory = new File(projectRootDirectory, ProjectDirectoryName)
	final val BootDirectory = new File(ProjectDirectory, BootDirectoryName)
	final val PropertiesFile = new File(ProjectDirectory, BuildPropertiesName)

	final def checkProject() =
	{
		if(ProjectDirectory.exists)
			None
		else
		{
			val line = SimpleReader.readLine("Project does not exist, create new project? (y/N/s) : ")
			if(isYes(line))
			{
				ProjectProperties(PropertiesFile, true)
				None
			}
			else if(isScratch(line))
			{
				ProjectProperties.scratch(PropertiesFile)
				None
			}
			else
				Some(new Exit(1))
		}
	}

	private val scalaLoaderCache = new scala.collection.jcl.WeakHashMap[String, ClassLoader]
	def launcher(directory: File, mainClassName: String): Launcher = new Launch(directory, mainClassName)
	def getScalaLoader(scalaVersion: String) = scalaLoaderCache.getOrElseUpdate(scalaVersion, createScalaLoader(scalaVersion))

	def createScalaLoader(scalaVersion: String): ClassLoader =
	{
		val baseDirectory = new File(BootDirectory, baseDirectoryName(scalaVersion))
		val scalaDirectory = new File(baseDirectory, ScalaDirectoryName)
		val scalaLoader = newScalaLoader(scalaDirectory)
		if(needsUpdate(scalaLoader, TestLoadScalaClasses))
		{
			(new Update(baseDirectory, "", scalaVersion))(UpdateScala)
			val scalaLoader = newScalaLoader(scalaDirectory)
			failIfMissing(scalaLoader, TestLoadScalaClasses, "Scala " + scalaVersion)
			scalaLoader
		}
		else
			scalaLoader
	}
	def createSbtLoader(sbtVersion: String, scalaVersion: String): ClassLoader = createSbtLoader(sbtVersion, scalaVersion, getScalaLoader(scalaVersion))
	def createSbtLoader(sbtVersion: String, scalaVersion: String, parentLoader: ClassLoader): ClassLoader =
	{
		val baseDirectory = new File(BootDirectory, baseDirectoryName(scalaVersion))
		val mainComponentLocation = componentLocation(sbtVersion, MainSbtComponentID, scalaVersion)
		val sbtLoader = newSbtLoader(mainComponentLocation, parentLoader)
		if(needsUpdate(sbtLoader, TestLoadSbtClasses))
		{
			(new Update(baseDirectory, sbtVersion, scalaVersion))(UpdateSbt)
			val sbtLoader = newSbtLoader(mainComponentLocation, parentLoader)
			failIfMissing(sbtLoader, TestLoadSbtClasses, "sbt " + sbtVersion)
			sbtLoader
		}
		else
			sbtLoader
	}
	private def newScalaLoader(dir: File) = newLoader(dir, new BootFilteredLoader(getClass.getClassLoader))
	private def newSbtLoader(dir: File, parentLoader: ClassLoader) = newLoader(dir, parentLoader)
}
private object Launch
{
	def apply(args: Array[String]) = (new Launch).boot(args)
	def isYes(so: Option[String]) = isValue("y", "yes")(so)
	def isScratch(so: Option[String]) = isValue("s", "scratch")(so)
	def isValue(values: String*)(so: Option[String]) =
		so match
		{
			case Some(s) => values.contains(s.toLowerCase)
			case None => false
		}
	private def failIfMissing(loader: ClassLoader, classes: Iterable[String], label: String) =
		checkTarget(loader, classes, (), throw new BootException("Could not retrieve " + label + "."))
	private def needsUpdate(loader: ClassLoader, classes: Iterable[String]) = checkTarget(loader, classes, false, true)
	private def checkTarget[T](loader: ClassLoader, classes: Iterable[String], ifSuccess: => T, ifFailure: => T): T =
	{
		try
		{
			classes.foreach { c => Class.forName(c, false, loader) }
			ifSuccess
		}
		catch { case e: ClassNotFoundException => ifFailure }
	}
	private def newLoader(directory: File, parent: ClassLoader) = new URLClassLoader(getJars(directory),  parent)
	private def getJars(directory: File) = wrapNull(directory.listFiles(JarFilter)).map(_.toURI.toURL)
	private def wrapNull(a: Array[File]): Array[File] = if(a == null) Array() else a
}
private object JarFilter extends FileFilter
{
	def accept(file: File) = !file.isDirectory && file.getName.endsWith(".jar")
}
final class Exit(val code: Int) extends IExit

// The exception to use when an error occurs at the launcher level (and not a nested exception).
// This indicates overrides toString because the exception class name is not needed to understand
// the error message.
private class BootException(override val toString: String) extends RuntimeException