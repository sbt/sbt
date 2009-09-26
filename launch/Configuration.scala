package xsbt.boot

import java.io.{File, FileInputStream, InputStreamReader}
import java.net.{URI, URL}

object Configuration
{
	def parse(file: URL, baseDirectory: File) = Using( new InputStreamReader(file.openStream, "utf8") )( (new ConfigurationParser(baseDirectory)).apply )
	def find(args: Seq[String], baseDirectory: File): (URL, Seq[String]) =
		args match
		{
			case Seq(head, tail @ _*) if head.startsWith("@")=> (configurationFromFile(head.substring(1), baseDirectory), tail)
			case _ => (configurationOnClasspath, args)
		}
	def configurationOnClasspath: URL =
	{
		resourcePaths.toStream.map(getClass.getResource).find(_ ne null) getOrElse
			( throw new BootException(resourcePaths.mkString("Could not finder sbt launch configuration. (Searched classpath for ", ",", ")")) )
	}
	def configurationFromFile(path: String, baseDirectory: File): URL =
	{
		def resolve(against: URI): Option[URL] =
		{
			val resolved = against.resolve(path)
			val exists = try { (new File(resolved)).exists } catch { case _: IllegalArgumentException => false }
			if(exists) Some(resolved.toURL) else None
		}
		resolveAgainst(baseDirectory).toStream.flatMap(resolve).firstOption.getOrElse(throw new BootException("Could not find configuration file '" + path + "'."))
	}

	val ConfigurationFilename = "sbt.boot.properties"
	val JarBasePath = "/sbt/"
	def userConfigurationPath = "/" + ConfigurationFilename
	def defaultConfigurationPath = JarBasePath + ConfigurationFilename
	def resourcePaths = Seq(userConfigurationPath, defaultConfigurationPath)
	def resolveAgainst(baseDirectory: File) = Seq(baseDirectory toURI, new File(System.getProperty("user.home")) toURI, classLocation(getClass).toURI)

	def classLocation(cl: Class[_]): URL =
	{
		val codeSource = cl.getProtectionDomain.getCodeSource
		if(codeSource == null) throw new BootException("No class location for " + cl)
		else codeSource.getLocation
	}
}