/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package xsbt.boot

import Pre._
import java.io.{File, FileInputStream, InputStreamReader}
import java.net.{MalformedURLException, URI, URL}
import scala.collection.immutable.List

object Configuration
{
	def parse(file: URL, baseDirectory: File) = Using( new InputStreamReader(file.openStream, "utf8") )( (new ConfigurationParser).apply )
	def find(args: List[String], baseDirectory: File): (URL, List[String]) =
		args match
		{
			case head :: tail if head.startsWith("@")=> (directConfiguration(head.substring(1), baseDirectory), tail)
			case _ =>
				val propertyConfigured = System.getProperty("sbt.boot.properties")
				val url = if(propertyConfigured == null) configurationOnClasspath else configurationFromFile(propertyConfigured, baseDirectory)
				(url , args)
		}
	def configurationOnClasspath: URL =
	{
		resourcePaths.iterator.map(getClass.getResource).find(_ ne null) getOrElse
			( multiPartError("Could not finder sbt launch configuration.  Searched classpath for:", resourcePaths))
	}
	def directConfiguration(path: String, baseDirectory: File): URL =
	{
		try { new URL(path) }
		catch { case _: MalformedURLException => configurationFromFile(path, baseDirectory) }
	}
	def configurationFromFile(path: String, baseDirectory: File): URL =
	{
		def resolve(against: URI): Option[URL] =
		{
			val resolved = against.resolve(path)
			val exists = try { (new File(resolved)).exists } catch { case _: IllegalArgumentException => false }
			if(exists) Some(resolved.toURL) else None
		}
		val against = resolveAgainst(baseDirectory)
		// use Iterators so that resolution occurs lazily, for performance
		val resolving = against.iterator.flatMap(e => resolve(e).toList.iterator)
		if(!resolving.hasNext) multiPartError("Could not find configuration file '" + path + "'.  Searched:", against)
		resolving.next()
	}
	def multiPartError[T](firstLine: String, lines: List[T]) = error( (firstLine :: lines).mkString("\n\t") )

	val ConfigurationName = "sbt.boot.properties"
	val JarBasePath = "/sbt/"
	def userConfigurationPath = "/" + ConfigurationName
	def defaultConfigurationPath = JarBasePath + ConfigurationName
	def resourcePaths: List[String] = userConfigurationPath :: defaultConfigurationPath :: Nil
	def resolveAgainst(baseDirectory: File): List[URI] = (baseDirectory toURI) :: (new File(System.getProperty("user.home")) toURI) ::
		toDirectory(classLocation(getClass).toURI) :: Nil

	def classLocation(cl: Class[_]): URL =
	{
		val codeSource = cl.getProtectionDomain.getCodeSource
		if(codeSource == null) error("No class location for " + cl)
		else codeSource.getLocation
	}
	def toDirectory(uri: URI): URI =
		try
		{
			val file = new File(uri)
			val newFile = if(file.isFile) file.getParentFile else file
			newFile.toURI
		}
		catch { case _: Exception => uri }
}