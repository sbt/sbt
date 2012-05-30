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
	private[boot] def readProperties(propertiesFile: File) =
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
		val scalaVersion = resolve(conf.scalaVersion)
		val appVersion = resolve(conf.app.version)
		val classifiers = resolveClassifiers(conf.ivyConfiguration.classifiers)
		resolveProxies(conf.withVersions(scalaVersion, appVersion, classifiers))
	}
	def resolveClassifiers(classifiers: Classifiers): Classifiers =
	{
		import ConfigurationParser.readIDs
		  // the added "" ensures that the main jars are retrieved
		val scalaClassifiers = "" :: resolve(classifiers.forScala)
		val appClassifiers = "" :: resolve(classifiers.app)
		Classifiers(new Explicit(scalaClassifiers), new Explicit(appClassifiers))
	}
  /** Resolves the proxy configuration and alters our launch configuration accordingly. */
	def resolveProxies(config: LaunchConfiguration): LaunchConfiguration = {
		import scala.util.control.Exception.catching
		def resolveUrl(name: String): Option[java.net.URL] =
			for {
			  name <- deepResolve(name)
			  url  <- catching(classOf[java.net.MalformedURLException]) opt new java.net.URL(name)
			} yield url
		// Pull the optional maven/ivy proxy configurations.
		// TODO - Precedence of using global first ok?
		val (maven, ivy) =
			resolveUrl("sbt.repo.proxy.url") match {
				case Some(url) =>  Some(url) -> Some(url)
				case _         => resolveUrl("sbt.repo.proxy.maven.url") -> resolveUrl("sbt.repo.proxy.ivy.url")
			}
		def addMavenProxy(c: LaunchConfiguration) = maven map (url => c.withMavenProxyRepository(url)) getOrElse c
		def addIvyProxy(c: LaunchConfiguration) = {
			val artifactPattern = deepResolve("sbt.repo.proxy.ivy.url.pattern") getOrElse Repository.defaultArtifactPattern
			val ivyPattern = deepResolve("sbt.repo.proxy.ivy.url.ivypattern") getOrElse artifactPattern
			ivy map (url => c.withIvyProxyRepository(url, ivyPattern, artifactPattern)) getOrElse c
		}
		// Precedence:  Global config first, then maven/ivy specific can override.
		addMavenProxy(addIvyProxy(config))
	}
  // TODO - We should resolve more this way, or make this some kind of canonical setting area that's accesible inside of defaults.scala....
	def deepResolve(name: String): Option[String] = (
	  trim(properties.getProperty(name)) orElse 
	  trim(System.getProperty(name)) orElse
	  trim(System.getenv(name))
	)
	def resolve[T](v: Value[T])(implicit read: String => T): T =
		v match
		{
			case e: Explicit[t] => e.value
			case i: Implicit[t] =>
				trim(properties.getProperty(i.name)) map read orElse
					i.default getOrElse ("No " + i.name + " specified in " + propertiesFile)
		}
}
