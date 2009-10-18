package xsbt.boot

import Pre._
import java.io.{File, FileInputStream, InputStreamReader}
import java.net.{URI, URL}

object Configuration
{
	def parse(file: URL, baseDirectory: File) = Using( new InputStreamReader(file.openStream, "utf8") )( (new ConfigurationParser).apply )
	def find(args: List[String], baseDirectory: File): (URL, List[String]) =
		args match
		{
			case head :: tail if head.startsWith("@")=> (configurationFromFile(head.substring(1), baseDirectory), tail)
			case _ =>
				val propertyConfigured = System.getProperty("sbt.boot.properties")
				val url = if(propertyConfigured == null) configurationOnClasspath else configurationFromFile(propertyConfigured, baseDirectory)
				(url , args)
		}
	def configurationOnClasspath: URL =
	{
		resourcePaths.elements.map(getClass.getResource).find(_ ne null) getOrElse
			( multiPartError("Could not finder sbt launch configuration.  Searched classpath for:", resourcePaths))
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
		val resolving = against.elements.flatMap(e => resolve(e).toList.elements)
		if(!resolving.hasNext) multiPartError("Could not find configuration file '" + path + "'.  Searched:", against)
		resolving.next()
	}
	def multiPartError[T](firstLine: String, lines: List[T]) = error( (firstLine :: lines).mkString("\n\t") )

	val ConfigurationName = "sbt.boot.properties"
	val JarBasePath = "/sbt/"
	def userConfigurationPath = "/" + ConfigurationName
	def defaultConfigurationPath = JarBasePath + ConfigurationName
	def resourcePaths = List(userConfigurationPath, defaultConfigurationPath)
	def resolveAgainst(baseDirectory: File) = List(baseDirectory toURI, new File(System.getProperty("user.home")) toURI, classLocation(getClass).toURI)

	def classLocation(cl: Class[_]): URL =
	{
		val codeSource = cl.getProtectionDomain.getCodeSource
		if(codeSource == null) error("No class location for " + cl)
		else codeSource.getLocation
	}
}