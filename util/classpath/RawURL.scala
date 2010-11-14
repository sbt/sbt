/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt
package classpath

	import java.io.{ByteArrayInputStream, InputStream}
	import java.net.{Proxy, URL, URLConnection, URLStreamHandler}
	import java.util.Enumeration

object RawURL
{
	def apply(file: String, value: String): URL =
		apply(file, value.getBytes)
	def apply(file: String, value: Array[Byte]): URL =
		apply(file)(new ByteArrayInputStream(value))
	def apply(file: String)(value: => InputStream): URL =
		new URL("raw", null, -1, file, new RawStreamHandler(value))

	private[this] final class RawStreamHandler(value: => InputStream) extends URLStreamHandler
	{
		override protected def openConnection(url: URL, p: Proxy): URLConnection =
			openConnection(url)
		override protected def openConnection(url: URL): URLConnection =
			new URLConnection(url)
			{
				private lazy val in = value
				def connect() { in }
				override def getInputStream = in
			}
	}
}

trait RawResources extends FixedResources
{
	protected def resources: Map[String, String]
	override protected final val resourceURL = resources.transform(RawURL.apply)
}
trait FixedResources extends ClassLoader
{
	protected def resourceURL: Map[String, URL]
	override def findResource(s: String): URL = resourceURL.getOrElse(s, super.findResource(s))

		import java.util.Collections.{enumeration, singletonList}
	override def findResources(s: String): Enumeration[URL] =
	{
		val sup = super.findResources(s)
		resourceURL.get(s) match
		{
			case Some(url) => new DualEnumeration(enumeration(singletonList(url)), sup)
			case None => sup
		}
	}
}