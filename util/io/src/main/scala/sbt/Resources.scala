/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah
 */
package sbt

import java.io.File
import IO._
import Resources.error

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
	def error(msg: String) = throw new ResourcesException(msg)
	private val LoadErrorPrefix = "Error loading initial project: "
}
class ResourcesException(msg: String) extends Exception(msg)

class Resources(val baseDirectory: File)
{
	import Resources._
	// The returned directory is not actually read-only, but it should be treated that way
	def readOnlyResourceDirectory(group: String, name: String): File =
	{
		val groupDirectory = new File(baseDirectory, group)
		if(groupDirectory.isDirectory)
		{
			val resourceDirectory = new File(groupDirectory, name)
			if(resourceDirectory.isDirectory)
				resourceDirectory
			else
				error("Resource directory '" + name + "' in group '" + group + "' not found.")
		}
		else
			error("Group '" + group + "' not found.")
	}
	def readWriteResourceDirectory[T](group: String, name: String)(withDirectory: File => T): T =
	{
		val file = readOnlyResourceDirectory(group, name)
		readWriteResourceDirectory(file)(withDirectory)
	}

	def readWriteResourceDirectory[T](readOnly: File)(withDirectory: File => T): T =
	{
		require(readOnly.isDirectory)
		def readWrite(readOnly: File)(temporary: File): T =
		{
			val readWriteDirectory = new File(temporary, readOnly.getName)
			copyDirectory(readOnly, readWriteDirectory)
			withDirectory(readWriteDirectory)
		}
		withTemporaryDirectory(readWrite(readOnly))
	}
}