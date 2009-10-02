package xsbt.boot

import java.io.{File, FileInputStream, InputStreamReader}
import java.net.{URI, URL}

object Configuration
{
	def parse(file: URL, baseDirectory: File) = Using( new InputStreamReader(file.openStream, "utf8") )( (new ConfigurationParser).apply )
	def find(args: Seq[String], baseDirectory: File): (URL, Seq[String]) =
		args match
		{
			case Seq(head, tail @ _*) if head.startsWith("@")=> (configurationFromFile(head.substring(1), baseDirectory), tail)
			case _ =>
				val propertyConfigured = System.getProperty("sbt.boot.properties")
				val url = if(propertyConfigured == null) configurationOnClasspath else configurationFromFile(propertyConfigured, baseDirectory)
				(url , args)
		}
	def configurationOnClasspath: URL =
	{
		resourcePaths.toStream.map(getClass.getResource).find(_ ne null) getOrElse
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
		against.toStream.flatMap(resolve).firstOption.getOrElse(multiPartError("Could not find configuration file '" + path + "'.  Searched:", against))
	}
	def multiPartError[T](firstLine: String, lines: Seq[T]) = throw new BootException( (Seq(firstLine) ++ lines).mkString("\n\t") )

	val ConfigurationName = "sbt.boot.properties"
	val JarBasePath = "/sbt/"
	def userConfigurationPath = "/" + ConfigurationName
	def defaultConfigurationPath = JarBasePath + ConfigurationName
	def resourcePaths = Seq(userConfigurationPath, defaultConfigurationPath)
	def resolveAgainst(baseDirectory: File) = Seq(baseDirectory toURI, new File(System.getProperty("user.home")) toURI, classLocation(getClass).toURI)

	def classLocation(cl: Class[_]): URL =
	{
		val codeSource = cl.getProtectionDomain.getCodeSource
		if(codeSource == null) throw new BootException("No class location for " + cl)
		else codeSource.getLocation
	}
}