/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package xsbt.boot

import Pre._
import java.io.{File, FileInputStream}
import java.util.Properties

object ResolveValues
{
	def apply(conf: LaunchConfiguration): LaunchConfiguration = (new ResolveValues(conf))()
	private def trim(s: String) = if(s eq null) None else notEmpty(s.trim)
	private def notEmpty(s: String) = if(isEmpty(s)) None else Some(s)
	private def readProperties(propertiesFile: File) =
	{
		val properties = new Properties
		if(propertiesFile.exists)
			Using( new FileInputStream(propertiesFile) )( properties.load )
		properties
	}
}

import ResolveValues.{readProperties, trim}
final class ResolveValues(conf: LaunchConfiguration)
{
	private def propertiesFile = conf.boot.properties
	private lazy val properties = readProperties(propertiesFile)
	def apply(): LaunchConfiguration =
	{
		import conf._
		val scalaVersion = resolve(conf.scalaVersion)
		val appVersion = resolve(app.version)
		val classifiers = resolveClassifiers(ivyConfiguration.classifiers)
		withVersions(scalaVersion, appVersion, classifiers)
	}
	def resolveClassifiers(classifiers: Classifiers): Classifiers =
	{
		import ConfigurationParser.readIDs
		  // the added "" ensures that the main jars are retrieved
		val scalaClassifiers = "" :: resolve(classifiers.forScala)
		val appClassifiers = "" :: resolve(classifiers.app)
		Classifiers(new Explicit(scalaClassifiers), new Explicit(appClassifiers))
	}
	def resolve[T](v: Value[T])(implicit read: String => T): T =
		v match
		{
			case e: Explicit[t] => e.value
			case i: Implicit[t] =>
				trim(properties.getProperty(i.name)) map read orElse
					i.default getOrElse
					error("No " + i.name + " specified in " + propertiesFile)
		}
}