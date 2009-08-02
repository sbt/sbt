/* sbt -- Simple Build Tool
 * Copyright 2008 Mark Harrah
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
	def apply(): Option[String] =
	{
		import configuration._
		def runJetty() =
		{
			val baseLoader = this.getClass.getClassLoader
			val classpathURLs = jettyClasspath.get.map(_.asURL).toSeq
			val loader: ClassLoader = new java.net.URLClassLoader(classpathURLs.toArray, baseLoader)
			val lazyLoader = new LazyFrameworkLoader(implClassName, Array(FileUtilities.sbtJar.toURI.toURL), loader, baseLoader)
			val runner = ModuleUtilities.getObject(implClassName, lazyLoader).asInstanceOf[JettyRun]
			runner(configuration)
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
	private val implClassName = "sbt.LazyJettyRun"
	
	private def runError(e: Throwable, messageBase: String, log: Logger) =
	{
		log.trace(e)
		Some(messageBase + e.toString)
	}
}

private trait Stoppable
{
	def stop(): Unit
}
private trait JettyRun
{
	def apply(configuration: JettyConfiguration): Stoppable
}
sealed trait JettyConfiguration extends NotNull
{
	def war: Path
	def scanDirectories: Seq[File]
	def scanInterval: Int
	/** The classpath to get Jetty from. */
	def jettyClasspath: PathFinder
	/** The classpath containing the classes, jars, and resources for the web application. */
	def classpath: PathFinder
	def classpathName: String
	def log: Logger
}
trait DefaultJettyConfiguration extends JettyConfiguration
{
	def contextPath: String
	def port: Int
}
abstract class CustomJettyConfiguration extends JettyConfiguration
{
	def jettyConfigurationFiles: Seq[File] = Nil
	def jettyConfigurationXML: NodeSeq = NodeSeq.Empty
}

/* This class starts Jetty.
* NOTE: DO NOT actively use this class.  You will see NoClassDefFoundErrors if you fail
*  to do so.Only use its name in JettyRun for reflective loading.  This allows using
*  the Jetty libraries provided on the project classpath instead of requiring them to be
*  available on sbt's classpath at startup.
*/
private object LazyJettyRun extends JettyRun
{
	import org.mortbay.jetty.{Handler, Server}
	import org.mortbay.jetty.nio.SelectChannelConnector
	import org.mortbay.jetty.webapp.WebAppContext
	import org.mortbay.log.Log
	import org.mortbay.util.Scanner
	import org.mortbay.xml.XmlConfiguration
	
	import java.lang.ref.{Reference, WeakReference}
	
	val DefaultMaxIdleTime = 30000
	
	def apply(configuration: JettyConfiguration): Stoppable =
	{
		val oldLog = Log.getLog
		Log.setLog(new JettyLogger(configuration.log))
		val server = new Server
		
		val listener =
			configuration match
			{
				case c: DefaultJettyConfiguration =>
					import c._
					configureDefaultConnector(server, port)
					def classpathURLs = classpath.get.map(_.asURL).toSeq
					def createLoader = new URLClassLoader(classpathURLs.toArray, this.getClass.getClassLoader)
					val webapp = new WebAppContext(war.absolutePath, contextPath)
					webapp.setClassLoader(createLoader)
					server.setHandler(webapp)
					
					Some(new Scanner.BulkListener {
						def filesChanged(files: java.util.List[_]) {
							reload(server, webapp.setClassLoader(createLoader), log)
						}
					})
				case c: CustomJettyConfiguration =>
					for(x <- c.jettyConfigurationXML)
						(new XmlConfiguration(x.toString)).configure(server)
					for(file <- c.jettyConfigurationFiles)
						(new XmlConfiguration(file.toURI.toURL)).configure(server)
					None
			}
		
		def configureScanner() =
		{
			val scanDirectories = configuration.scanDirectories
			if(listener.isEmpty || scanDirectories.isEmpty)
				None
			else
			{
				configuration.log.debug("Scanning for changes to: " + scanDirectories.mkString(", "))
				val scanner = new Scanner
				val list = new java.util.ArrayList[File]
				scanDirectories.foreach(x => list.add(x))
				scanner.setScanDirs(list)
				scanner.setRecursive(true)
				scanner.setScanInterval(configuration.scanInterval)
				scanner.setReportExistingFilesOnStartup(false)
				scanner.addListener(listener.get)
				scanner.start()
				Some(new WeakReference(scanner))
			}
		}
		
		try
		{
			server.start()
			new StopServer(new WeakReference(server), configureScanner(), oldLog)
		}
		catch { case e => server.stop(); throw e }
	}
	private def configureDefaultConnector(server: Server, port: Int)
	{
		val defaultConnector = new SelectChannelConnector
		defaultConnector.setPort(port)
		defaultConnector.setMaxIdleTime(DefaultMaxIdleTime)
		server.addConnector(defaultConnector)
	}
	private class StopServer(serverReference: Reference[Server], scannerReferenceOpt: Option[Reference[Scanner]], oldLog: org.mortbay.log.Logger) extends Stoppable
	{
		def stop()
		{
			val server = serverReference.get
			if(server != null)
				server.stop()
			for(scannerReference <- scannerReferenceOpt)
			{
				val scanner = scannerReference.get
				if(scanner != null)
					scanner.stop()
			}
			Log.setLog(oldLog)
		}
	}
	private def reload(server: Server, reconfigure: => Unit, log: Logger)
	{
		log.info("Reloading web application...")
		val handlers = wrapNull(server.getHandlers, server.getHandler)
		log.debug("Stopping handlers: " + handlers.mkString(", "))
		handlers.foreach(_.stop)
		log.debug("Reconfiguring...")
		reconfigure
		log.debug("Restarting handlers: " + handlers.mkString(", "))
		handlers.foreach(_.start)
		log.info("Reload complete.")
	}
	private def wrapNull(a: Array[Handler], b: Handler) =
		(a, b) match
		{
			case (null, null) => Nil
			case (null, notB) => notB :: Nil
			case (notA, null) => notA.toList
			case (notA, notB) => notB :: notA.toList
		}
	private class JettyLogger(delegate: Logger) extends org.mortbay.log.Logger
	{
		def isDebugEnabled = delegate.atLevel(Level.Debug)
		def setDebugEnabled(enabled: Boolean) = delegate.setLevel(if(enabled) Level.Debug else Level.Info)
	
		def getLogger(name: String) = this
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
}
