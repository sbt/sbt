/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package sbt.boot

/*
Project does not exist, create new project? [y/N/s] y
Name: 
Organization []:
Version [1.0]: 
Scala version [2.7.5]: 
sbt version [0.5.3]: 
*/
import java.io.File
/** Constants related to reading/writing the build.properties file in a project.
* See BootConfiguration for general constants used by the loader. */
private object ProjectProperties
{
	/** The properties key for storing the name of the project.*/
	val NameKey = "project.name"
	/** The properties key for storing the organization of the project.*/
	val OrganizationKey = "project.organization"
	/** The properties key for storing the version of the project.*/
	val VersionKey = "project.version"
	/** The properties key for storing the version of Scala used with the project.*/
	val ScalaVersionKey = "scala.version"
	/** The properties key for storing the version of sbt used to build the project.*/
	val SbtVersionKey = "sbt.version"
	/** The properties key to communicate to the main component of sbt that the project
	* should be initialized after being loaded, typically by creating a default directory structure.*/
	val InitializeProjectKey = "project.initialize"
	/** The properties key that configures the project to be flattened a bit for use by quick throwaway projects.*/
	val ScratchKey = "project.scratch"
	
	/** The label used when prompting for the name of the user's project.*/
	val NameLabel = "Name"
	/** The label used when prompting for the organization of the user's project.*/
	val OrganizationLabel = "Organization"
	/** The label used when prompting for the version of the user's project.*/
	val VersionLabel = "Version"
	/** The label used when prompting for the version of Scala to use for the user's project.*/
	val ScalaVersionLabel = "Scala version"
	/** The label used when prompting for the version of sbt to use for the user's project.*/
	val SbtVersionLabel = "sbt version"
	
	/** The default organization of the new user project when the user doesn't explicitly specify one when prompted.*/
	val DefaultOrganization = ""
	/** The default version of the new user project when the user doesn't explicitly specify a version when prompted.*/
	val DefaultVersion = "1.0"
	/** The default version of sbt when the user doesn't explicitly specify a version when prompted.*/
	val DefaultSbtVersion = "0.5.3"
	/** The default version of Scala when the user doesn't explicitly specify a version when prompted.*/
	val DefaultScalaVersion = "2.7.5"

	// sets up the project properties for a throwaway project (flattens src and lib to the root project directory)
	def scratch(file: File)
	{
		withProperties(file) { properties =>
			for( (key, _, default, _) <- propertyDefinitions(false))
				properties(key) = default.getOrElse("scratch")
			properties(ScratchKey) = true.toString
		}
	}
	// returns (scala version, sbt version)
	def apply(file: File, setInitializeProject: Boolean): (String, String) = applyImpl(file, setInitializeProject, Nil)
	def forcePrompt(file: File, propertyKeys: String*) = applyImpl(file, false, propertyKeys)
	private def applyImpl(file: File, setInitializeProject: Boolean, propertyKeys: Iterable[String]): (String, String) =
	{
		val organizationOptional = file.exists
		withProperties(file) { properties =>
			properties -= propertyKeys
			
			prompt(properties, organizationOptional)
			if(setInitializeProject)
				properties(InitializeProjectKey) = true.toString
		}
	}
	// (key, label, defaultValue, promptRequired)
	private def propertyDefinitions(organizationOptional: Boolean) = 
		(NameKey, NameLabel, None, true) :: 
		(OrganizationKey, OrganizationLabel, Some(DefaultOrganization), !organizationOptional) ::
		(VersionKey, VersionLabel, Some(DefaultVersion), true) ::
		(ScalaVersionKey, ScalaVersionLabel, Some(DefaultScalaVersion), true) ::
		(SbtVersionKey, SbtVersionLabel, Some(DefaultSbtVersion), true) ::
		Nil
	private def prompt(fill: ProjectProperties, organizationOptional: Boolean)
	{
		for( (key, label, default, promptRequired) <- propertyDefinitions(organizationOptional))
		{
			val value = fill(key)
			if(value == null && promptRequired)
				fill(key) = readLine(label, default)
		}
	}
	private def withProperties(file: File)(f: ProjectProperties => Unit) =
	{
		val properties = new ProjectProperties(file)
		f(properties)
		properties.save
		(properties(ScalaVersionKey), properties(SbtVersionKey))
	}
	private def readLine(label: String, default: Option[String]): String =
	{
		val prompt =
			default match
			{
				case Some(d) => "%s [%s]: ".format(label, d)
				case None => "%s: ".format(label)
			}
		SimpleReader.readLine(prompt) orElse default match
		{
			case Some(line) => line
			case None => throw new BootException("Project not loaded: " + label + " not specified.")
		}
	}
}

import java.io.{FileInputStream, FileOutputStream}
import java.util.Properties
private class ProjectProperties(file: File) extends NotNull
{
	private[this] var modified = false
	private[this] val properties = new Properties
	if(file.exists)
	{
		val in = new FileInputStream(file)
		try { properties.load(in) } finally { in.close() }
	}
	
	def update(key: String, value: String)
	{
		modified = true
		properties.setProperty(key, value)
	}
	def apply(key: String) = properties.getProperty(key)
	def save()
	{
		if(modified)
		{
			file.getParentFile.mkdirs()
			val out = new FileOutputStream(file)
			try { properties.store(out, "Project Properties") } finally { out.close() }
			modified = false
		}
	}
	def -= (keys: Iterable[String]) {  for(key <- keys) properties.remove(key)  }
}