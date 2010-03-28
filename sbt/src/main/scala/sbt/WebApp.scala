/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt

import java.io.File
import java.net.{URL, URLClassLoader}
import scala.xml.NodeSeq

object JettyRunner
{
	val DefaultPort = 8080
	val DefaultScanInterval = 3
}
class JettyRunner(configuration: JettyConfiguration) extends ExitHook
{
	ExitHooks.register(this)

	def name = "jetty-shutdown"
	def runBeforeExiting() { stop() }
	private var running: Option[Stoppable] = None
	private def started(s: Stoppable) { running = Some(s) }
	def stop()
	{
		running.foreach(_.stop())
		running = None
	}
	def reload() = running.foreach(_.reload())
	def apply(): Option[String] =
	{
		import configuration._
		def runJetty() =
		{
			val baseLoader = this.getClass.getClassLoader
			val jettyParentLoader = configuration match { case d: DefaultJettyConfiguration => d.parentLoader; case _ => ClassLoader.getSystemClassLoader }
			val jettyLoader: ClassLoader = ClasspathUtilities.toLoader(jettyClasspath, jettyParentLoader)
			
			val jettyFilter = (name: String) => name.startsWith("org.mortbay.") || name.startsWith("org.eclipse.jetty.")
			val notJettyFilter = (name: String) => !jettyFilter(name)
			
			val dual = new xsbt.DualLoader(baseLoader, notJettyFilter, x => true, jettyLoader, jettyFilter, x => false)
			
			def createRunner(implClassName: String) =
			{
				val lazyLoader = new LazyFrameworkLoader(implClassName, Array(FileUtilities.classLocation[Stoppable].toURI.toURL), dual, baseLoader)
				ModuleUtilities.getObject(implClassName, lazyLoader).asInstanceOf[JettyRun]
			}
			val runner = try { createRunner(implClassName6) } catch { case e: NoClassDefFoundError => createRunner(implClassName7) }
			runner(configuration, jettyLoader)
		}

		if(running.isDefined)
			Some("This instance of Jetty is already running.")
		else
		{
			try
			{
				started(runJetty())
				None
			}
			catch
			{
				case e: NoClassDefFoundError => runError(e, "Jetty and its dependencies must be on the " + classpathName + " classpath: ", log)
				case e => runError(e, "Error running Jetty: ", log)
			}
		}
	}
	private val implClassName6 = "sbt.jetty.LazyJettyRun6"
	private val implClassName7 = "sbt.jetty.LazyJettyRun7"

	private def runError(e: Throwable, messageBase: String, log: Logger) =
	{
		log.trace(e)
		Some(messageBase + e.toString)
	}
}

private trait Stoppable
{
	def stop(): Unit
	def reload(): Unit
}
private trait JettyRun
{
	def apply(configuration: JettyConfiguration, jettyLoader: ClassLoader): Stoppable
}
sealed trait JettyConfiguration extends NotNull
{
	/** The classpath to get Jetty from. */
	def jettyClasspath: PathFinder
	def classpathName: String
	def log: Logger
}
trait DefaultJettyConfiguration extends JettyConfiguration
{
	def war: Path
	def scanDirectories: Seq[File]
	def scanInterval: Int

	def contextPath: String
	def port: Int
	/** The classpath containing the classes, jars, and resources for the web application. */
	def classpath: PathFinder
	def parentLoader: ClassLoader
}
abstract class CustomJettyConfiguration extends JettyConfiguration
{
	def jettyConfigurationFiles: Seq[File] = Nil
	def jettyConfigurationXML: NodeSeq = NodeSeq.Empty
}

private class JettyLoggerBase(delegate: Logger)
{
	def getName = "JettyLogger"
	def isDebugEnabled = delegate.atLevel(Level.Debug)
	def setDebugEnabled(enabled: Boolean) = delegate.setLevel(if(enabled) Level.Debug else Level.Info)

	def info(msg: String) { delegate.info(msg) }
	def debug(msg: String) { delegate.warn(msg) }
	def warn(msg: String) { delegate.warn(msg) }
	def info(msg: String, arg0: AnyRef, arg1: AnyRef) { delegate.info(format(msg, arg0, arg1)) }
	def debug(msg: String, arg0: AnyRef, arg1: AnyRef) { delegate.debug(format(msg, arg0, arg1)) }
	def warn(msg: String, arg0: AnyRef, arg1: AnyRef) { delegate.warn(format(msg, arg0, arg1)) }
	def warn(msg: String, th: Throwable)
	{
		delegate.warn(msg)
		delegate.trace(th)
	}
	def debug(msg: String, th: Throwable)
	{
		delegate.debug(msg)
		delegate.trace(th)
	}
	private def format(msg: String, arg0: AnyRef, arg1: AnyRef) =
	{
		def toString(arg: AnyRef) = if(arg == null) "" else arg.toString
		val pieces = msg.split("""\{\}""", 3)
		if(pieces.length == 1)
			pieces(0)
		else
		{
			val base = pieces(0) + toString(arg0) + pieces(1)
			if(pieces.length == 2)
				base
			else
				base + toString(arg1) + pieces(2)
		}
	}
}