/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt

import java.io.File
import java.net.{URL, URLClassLoader}
import scala.xml.NodeSeq
import classpath.ClasspathUtilities

object JettyRunner
{
	val DefaultPort = 8080
	val DefaultScanInterval = 3
}
class JettyRunner(configuration: JettyConfiguration) extends ExitHook
{
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
			
			val dual = new classpath.DualLoader(baseLoader, notJettyFilter, x => true, jettyLoader, jettyFilter, x => false)
			
			def createRunner(implClassName: String) =
			{
				val lazyLoader = new classpath.LazyFrameworkLoader(implClassName, Array(IO.classLocation[Stoppable].toURI.toURL), dual, baseLoader)
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
	def log: AbstractLogger
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
	def jettyEnv: Option[File]
	def webDefaultXml: Option[File]
}
abstract class CustomJettyConfiguration extends JettyConfiguration
{
	def jettyConfigurationFiles: Seq[File] = Nil
	def jettyConfigurationXML: NodeSeq = NodeSeq.Empty
}

private class JettyLoggerBase(delegate: AbstractLogger)
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
	def info(msg: String, args: Array[AnyRef]) { delegate.info(format(msg, args: _*)) }
	def debug(msg: String, args: Array[AnyRef]) { delegate.debug(format(msg, args: _*)) }
	def warn(msg: String, args: Array[AnyRef]) { delegate.warn(format(msg, args: _*)) }
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
	private def format(msg: String, args: AnyRef*) =
	{
		def toString(arg: AnyRef) = if(arg == null) "" else arg.toString
		val pieces = msg.split("""\{\}""", args.length + 1).toList
		val argStrs = args.map(toString).toList ::: List("")
		pieces.zip(argStrs).foldLeft(new StringBuilder) { (sb, pair) =>
			val (piece, argStr) = pair
			if (piece.isEmpty) sb
			else sb.append(piece).append(argStr)
		}.toString
	}
}
