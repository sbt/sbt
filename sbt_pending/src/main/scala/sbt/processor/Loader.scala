/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt.processor

import java.io.File
import java.net.{URL, URLClassLoader}
import xsbt.FileUtilities.read
import xsbt.OpenResource.urlInputStream
import xsbt.Paths._
import xsbt.GlobFilter._

import ProcessorException.error

class Loader extends NotNull
{
	def classNameResource = "sbt.processor"
	def getProcessor(directory: File): Either[Throwable, Processor] = getProcessor( getLoader(directory) )
	private def getProcessor(loader: ClassLoader): Either[Throwable, Processor] =
	{
		val resource = loader.getResource(classNameResource)
		if(resource eq null) Left(new ProcessorException("Processor existed but did not contain '" + classNameResource + "' descriptor."))
		else loadProcessor(loader, resource)
	}
	private def loadProcessor(loader: ClassLoader, resource : URL): Either[Throwable, Processor] =
		try { Right(loadProcessor(loader, className(resource))) }
		catch { case e: Exception => Left(e) }
		
	private def loadProcessor(loader: ClassLoader, className: String): Processor =
	{
		val processor = Class.forName(className, true, loader).newInstance
		classOf[Processor].cast(processor)
	}
	private def className(resource: URL): String = urlInputStream(resource) { in => read(in).trim }
	private def getLoader(dir: File) =
	{
		val jars = dir ** "*.jar"
		val jarURLs = jars.files.toArray[File].map(_.toURI.toURL)
		new URLClassLoader(jarURLs, getClass.getClassLoader)
	}
}