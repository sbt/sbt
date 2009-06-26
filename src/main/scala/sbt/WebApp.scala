/* sbt -- Simple Build Tool
 * Copyright 2008 Mark Harrah
 */
package sbt

import java.io.File
import java.net.{URL, URLClassLoader}
import scala.xml.NodeSeq

object JettyRun extends ExitHook
{
	val DefaultPort = 8080
	
	ExitHooks.register(this)
	
	def name = "jetty-shutdown"
	def runBeforeExiting() { stop() }
	private var running: Option[Stoppable] = None
	private def started(s: Stoppable) { running = Some(s) }
	def stop()
	{
		synchronized
		{
			running.foreach(_.stop())
			running = None
		}
	}
	def apply(classpath: Iterable[Path], classpathName: String, war: Path, defaultContextPath: String, jettyConfigurationXML: NodeSeq,
		jettyConfigurationFiles: Seq[File], log: Logger): Option[String] =
			run(classpathName, new JettyRunConfiguration(war, defaultContextPath, DefaultPort, jettyConfigurationXML,
				jettyConfigurationFiles, Nil, 0, toURLs(classpath)), log)
	def apply(classpath: Iterable[Path], classpathName: String, war: Path, defaultContextPath: String, port: Int, scanDirectories: Seq[File],
		scanPeriod: Int, log: Logger): Option[String] =
			run(classpathName, new JettyRunConfiguration(war, defaultContextPath, port, NodeSeq.Empty, Nil, scanDirectories, scanPeriod, toURLs(classpath)), log)
	private def toURLs(paths: Iterable[Path]) = paths.map(_.asURL).toSeq
	private def run(classpathName: String, configuration: JettyRunConfiguration, log: Logger): Option[String] =
		synchronized
		{
			import configuration._
			def runJetty() =
			{
				val baseLoader = this.getClass.getClassLoader
				val loader: ClassLoader = new SelectiveLoader(classpathURLs.toArray, baseLoader, "org.mortbay." :: "javax.servlet." :: Nil)
				val lazyLoader = new LazyFrameworkLoader(implClassName, Array(FileUtilities.sbtJar.toURI.toURL), loader, baseLoader)
				val runner = ModuleUtilities.getObject(implClassName, lazyLoader).asInstanceOf[JettyRun]
				runner(configuration, log)
			}
			
			if(running.isDefined)
				Some("Jetty is already running.")
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
	def apply(configuration: JettyRunConfiguration, log: Logger): Stoppable
}
private class JettyRunConfiguration(val war: Path, val defaultContextPath: String, val port: Int,
	val jettyConfigurationXML: NodeSeq, val jettyConfigurationFiles: Seq[File],
	val scanDirectories: Seq[File], val scanInterval: Int, val classpathURLs: Seq[URL]) extends NotNull

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
	
	def apply(configuration: JettyRunConfiguration, log: Logger): Stoppable =
	{
		import configuration._
		val oldLog = Log.getLog
		Log.setLog(new JettyLogger(log))
		val server = new Server
		val useDefaults = jettyConfigurationXML.isEmpty && jettyConfigurationFiles.isEmpty
		
		val listener =
			if(useDefaults)
			{
				configureDefaultConnector(server, port)
				def createLoader = new URLClassLoader(classpathURLs.toArray, this.getClass.getClassLoader)
				val webapp = new WebAppContext(war.absolutePath, defaultContextPath)
				webapp.setClassLoader(createLoader)
				server.setHandler(webapp)
				
				Some(new Scanner.BulkListener {
					def filesChanged(files: java.util.List[_]) {
						reload(server, webapp.setClassLoader(createLoader), log)
					}
				})
			}
			else
			{
				for(x <- jettyConfigurationXML)
					(new XmlConfiguration(x.toString)).configure(server)
				for(file <- jettyConfigurationFiles)
					(new XmlConfiguration(file.toURI.toURL)).configure(server)
				None
			}
		
		def configureScanner() =
		{
			if(listener.isEmpty || scanDirectories.isEmpty)
				None
			else
			{
				log.debug("Scanning for changes to: " + scanDirectories.mkString(", "))
				val scanner = new Scanner
				val list = new java.util.ArrayList[File]
				scanDirectories.foreach(x => list.add(x))
				scanner.setScanDirs(list)
				scanner.setRecursive(true)
				scanner.setScanInterval(scanInterval)
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
		JettyRun.synchronized
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
