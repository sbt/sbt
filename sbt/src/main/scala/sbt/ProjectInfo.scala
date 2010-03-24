/* sbt -- Simple Build Tool
 * Copyright 2008 Mark Harrah
 */
package sbt

import java.io.File
import xsbti.{AppProvider, ScalaProvider}
import FileUtilities._

/** Represents the minimal information necessary to construct a Project.
*
* `projectDirectory` is the base directory for the project (not the root project directory)
* `dependencies` are the Projects that this Project depends on.
* `parent` is the parent Project, or None if this is the root project.
* `log` is the Logger to use as a base for the default project Logger.
* `buildScalaVersion` contains the explicitly requested Scala version to use  for building (as when using `+` or `++`) or None if the normal version should be used.
*/
final case class ProjectInfo(projectDirectory: File, dependencies: Iterable[Project], parent: Option[Project])
	(log: Logger, val app: AppProvider, val buildScalaVersion: Option[String]) extends NotNull
{
	/** The version of Scala running sbt.*/
	def definitionScalaVersion = app.scalaProvider.version
	/** The launcher instance that booted sbt.*/
	def launcher = app.scalaProvider.launcher

	val logger = log
	/** The base path for the project, preserving information to the root project directory.*/
	val projectPath: Path =
	{
		val toRoot = parent.flatMap(p => Path.relativize(p.info.projectPath, projectDirectory))
		new ProjectDirectory(projectDirectory, toRoot)
	}
	/** The path to build information.  The current location is `project/`.
	* Note: The directory used to be `metadata/`, hence the name of the constant in the implementation.
	* Note 2: Although it is called builderPath, it is not the path to the builder definition, which is `builderProjectPath`*/
	val builderPath = projectPath / ProjectInfo.MetadataDirectoryName
	/** The boot directory contains the jars needed for building the project, including Scala, sbt, processors and dependencies of these.*/
	def bootPath = builderPath / Project.BootDirectoryName
	/** The path to the build definition project. */
	def builderProjectPath = builderPath / Project.BuilderProjectDirectoryName
	def builderProjectOutputPath = builderProjectPath / Project.DefaultOutputDirectoryName
	/** The path to the plugin definition project. This declares the plugins to use for the build definition.*/
	def pluginsPath = builderPath / Project.PluginProjectDirectoryName
	def pluginsOutputPath = pluginsPath / Project.DefaultOutputDirectoryName
	/** The path to which the source code for plugins are extracted.*/
	def pluginsManagedSourcePath = pluginsPath / BasicDependencyPaths.DefaultManagedSourceDirectoryName
	/** The path to which plugins are retrieved.*/
	def pluginsManagedDependencyPath = pluginsPath / BasicDependencyPaths.DefaultManagedDirectoryName

	/** The classpath containing all jars comprising sbt, except for the launcher.*/
	def sbtClasspath = Path.finder(app.mainClasspath)
}

private[sbt] sealed trait SetupResult extends NotNull
private[sbt] final object SetupDeclined extends SetupResult
private[sbt] final class SetupError(val message: String) extends SetupResult
private[sbt] final object AlreadySetup extends SetupResult
private[sbt] final class SetupInfo(val name: String, val version: Option[Version], val organization: Option[String], val initializeDirectories: Boolean) extends SetupResult

object ProjectInfo
{
	val MetadataDirectoryName = "project"
	private val DefaultOrganization = "empty"

	def setup(info: ProjectInfo, log: Logger): SetupResult =
	{
		val builderDirectory = info.builderPath.asFile
		if(builderDirectory.exists)
		{
			if(builderDirectory.isDirectory)
				AlreadySetup
			else
				new SetupError("'" + builderDirectory.getAbsolutePath + "' is not a directory.")
		}
		else
			setupProject(info.projectDirectory, log)
	}
	private def setupProject(projectDirectory: File, log: Logger): SetupResult =
	{
		if(confirmPrompt("No project found. Create new project?", false))
		{
			val name = trim(SimpleReader.readLine("Project Name: "))
			if(name.isEmpty)
				new SetupError("Project not created: no name specified.")
			else
			{
				val organization =
				{
					val org = trim(SimpleReader.readLine("Organization [" + DefaultOrganization + "]: "))
					if(org.isEmpty)
						DefaultOrganization
					else
						org
				}
				readVersion(projectDirectory, log) match
				{
					case None => new SetupError("Project not created: no version specified.")
					case Some(version) =>
						if(verifyCreateProject(name, version, organization))
							new SetupInfo(name, Some(version), Some(organization), true)
						else
							SetupDeclined
				}
			}
		}
		else
			SetupDeclined
	}
	private def verifyCreateProject(name: String, version: Version, organization: String): Boolean =
		confirmPrompt("Create new project " + name + " " + version + " with organization " + organization +" ?", true)

	private def confirmPrompt(question: String, defaultYes: Boolean) =
	{
		val choices = if(defaultYes) " (Y/n) " else " (y/N) "
		val answer = trim(SimpleReader.readLine(question + choices))
		val yes = "y" :: "yes" :: (if(defaultYes) List("") else Nil)
		yes.contains(answer.toLowerCase)
	}

	private def readVersion(projectDirectory: File, log: Logger): Option[Version] =
	{
		val version = trim(SimpleReader.readLine("Version: "))
		if(version.isEmpty)
			None
		else
		{
			Version.fromString(version) match
			{
				case Left(errorMessage) =>
				{
					log.error("Invalid version: " + errorMessage)
					readVersion(projectDirectory, log)
				}
				case Right(v) => Some(v)
			}
		}
	}
	private def trim(s: Option[String]) = s.getOrElse("")
}