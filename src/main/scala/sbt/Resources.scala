/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah
 */
package sbt

import java.io.File
import xsbti.AppProvider
import FileUtilities._

object Resources
{
	def apply(basePath: String) =
	{
		require(basePath.startsWith("/"))
		val resource = getClass.getResource(basePath)
		if(resource == null)
			error("Resource base directory '" + basePath + "' not on classpath.")
		else
		{
			val file = toFile(resource)
			if(file.exists)
				new Resources(file)
			else
				error("Resource base directory '" + basePath + "' does not exist.")
		}
	}
	private val LoadErrorPrefix = "Error loading initial project: "
}

class Resources(val baseDirectory: File)
{
	import Resources._
	// The returned directory is not actually read-only, but it should be treated that way
	def readOnlyResourceDirectory(group: String, name: String): Either[String, File] =
	{
		val groupDirectory = new File(baseDirectory, group)
		if(groupDirectory.isDirectory)
		{
			val resourceDirectory = new File(groupDirectory, name)
			if(resourceDirectory.isDirectory)
				Right(resourceDirectory)
			else
				Left("Resource directory '" + name + "' in group '" + group + "' not found.")
		}
		else
			Left("Group '" + group + "' not found.")
	}
	def readWriteResourceDirectory[T](group: String, name: String, log: Logger)
		(withDirectory: File => Either[String, T]): Either[String, T] =
			readOnlyResourceDirectory(group, name).right flatMap(file => readWriteResourceDirectory(file, log)(withDirectory))
	def readWriteResourceDirectory[T](readOnly: File, log: Logger)
		(withDirectory: File => Either[String, T]): Either[String, T] =
	{
		require(readOnly.isDirectory)
		def readWrite(readOnly: File)(temporary: File): Either[String, T] =
		{
			val readWriteDirectory = new File(temporary, readOnly.getName)
			FileUtilities.copyDirectory(readOnly, readWriteDirectory, log).toLeft(()).right flatMap { x =>
				withDirectory(readWriteDirectory)
			}
		}
		doInTemporaryDirectory(log)(readWrite(readOnly))
	}
}