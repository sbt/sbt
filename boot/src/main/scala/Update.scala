/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package sbt.boot

import java.io.{File, FileWriter, PrintWriter, Writer}

import org.apache.ivy.{core, plugins, util, Ivy}
import core.LogOptions
import core.cache.DefaultRepositoryCacheManager
import core.event.EventManager
import core.module.id.ModuleRevisionId
import core.module.descriptor.{Configuration, DefaultDependencyDescriptor, DefaultModuleDescriptor, ModuleDescriptor}
import core.report.ResolveReport
import core.resolve.{ResolveEngine, ResolveOptions}
import core.retrieve.{RetrieveEngine, RetrieveOptions}
import core.sort.SortEngine
import core.settings.IvySettings
import plugins.resolver.{ChainResolver, FileSystemResolver, IBiblioResolver, URLResolver}
import util.{DefaultMessageLogger, Message}

import BootConfiguration._

private[boot] object UpdateTarget extends Enumeration
{
	val UpdateScala, UpdateSbt = Value
}
import UpdateTarget.{UpdateSbt, UpdateScala}

object Update
{
	/** Use Ivy to resolve and retrieve the specified 'targets' for the given versions.*/
	def apply(bootDirectory: File, sbtVersion: String, scalaVersion: String, targets: UpdateTarget.Value*) =
		synchronized // synchronized because Ivy is not thread-safe
		{
			val up = new Update(bootDirectory, sbtVersion, scalaVersion, targets : _*)
			up.update()
		}
}
/** Ensures that the Scala and sbt jars exist for the given versions or else downloads them.*/
private final class Update(bootDirectory: File, sbtVersion: String, scalaVersion: String, targets: UpdateTarget.Value*)
{
	private def logFile = new File(bootDirectory, UpdateLogName)
	/** A Writer to use to write the full logging information to a file for debugging. **/
	lazy val logWriter =
	{
		bootDirectory.mkdirs
		new PrintWriter(new FileWriter(logFile))
	}

	/** The main entry point of this class for use by the Update module.  It runs Ivy */
	private def update()
	{
		Message.setDefaultLogger(new SbtIvyLogger(logWriter))
		try { targets.foreach(update) } // runs update on each module separately
		catch
		{
			case e: Exception =>
				e.printStackTrace(logWriter)
				log(e.toString)
				println("  (see " + logFile + " for complete log)")
		}
		finally { logWriter.close() }
	}
	/** Runs update for the specified target (updates either the scala or sbt jars for building the project) */
	private def update(target: UpdateTarget.Value)
	{
		val settings = new IvySettings
		val ivy = Ivy.newInstance(settings)
		ivy.pushContext()
		try { update(target, settings) }
		finally { ivy.popContext() }
	}
	private def update(target: UpdateTarget.Value, settings: IvySettings)
	{
		import Configuration.Visibility.PUBLIC
		// the actual module id here is not that important
		val moduleID = new DefaultModuleDescriptor(createID(SbtOrg, "boot", "1.0"), "release", null, false)
		moduleID.setLastModified(System.currentTimeMillis)
		moduleID.addConfiguration(new Configuration(DefaultIvyConfiguration, PUBLIC, "", Array(), true, null))
		// add dependencies based on which target needs updating
		target match
		{
			case UpdateScala =>
				addDependency(moduleID, ScalaOrg, CompilerModuleName, scalaVersion, "default")
				addDependency(moduleID, ScalaOrg, LibraryModuleName, scalaVersion, "default")
				update(settings, moduleID, target, false)
			case UpdateSbt =>
				addDependency(moduleID, SbtOrg, SbtModuleName, sbtVersion, scalaVersion)
				try { update(settings, moduleID, target, false) }
				catch
				{
					 // unfortunately, there is not a more specific exception thrown when a configuration does not exist,
					 // so we always retry after cleaning the ivy file for this version of sbt on in case it is a newer version
					 // of Scala than when this version of sbt was initially published
					case e: RuntimeException =>
						update(settings, moduleID, target, true)
				}
		}
	}
	/** Runs the resolve and retrieve for the given moduleID, which has had its dependencies added already. */
	private def update(settings: IvySettings, moduleID: DefaultModuleDescriptor,  target: UpdateTarget.Value, cleanExisting: Boolean)
	{
		val eventManager = new EventManager
		addResolvers(settings, scalaVersion, target)
		settings.setDefaultConflictManager(settings.getConflictManager(ConflictManagerName))
		settings.setBaseDir(bootDirectory)
		if(cleanExisting)
		{
			val sbtID = createID(SbtOrg, SbtModuleName, sbtVersion)
			onDefaultRepositoryCacheManager(settings) { cache =>
				val ivyFile = cache.getIvyFileInCache(sbtID)
				ivyFile.delete()
				val original = new File(ivyFile.getParentFile, ivyFile.getName + ".original")
				original.delete()
			}
		}
		resolve(settings, eventManager, moduleID)
		retrieve(settings, eventManager, moduleID, target)
	}
	private def createID(organization: String, name: String, revision: String) =
		ModuleRevisionId.newInstance(organization, name, revision)
	/** Adds the given dependency to the default configuration of 'moduleID'. */
	private def addDependency(moduleID: DefaultModuleDescriptor, organization: String, name: String, revision: String, conf: String)
	{
		val dep = new DefaultDependencyDescriptor(moduleID, createID(organization, name, revision), false, false, true)
		dep.addDependencyConfiguration(DefaultIvyConfiguration, conf)
		moduleID.addDependency(dep)
	}
	private def resolve(settings: IvySettings, eventManager: EventManager, module: ModuleDescriptor)
	{
		val resolveOptions = new ResolveOptions
		  // this reduces the substantial logging done by Ivy, including the progress dots when downloading artifacts
		resolveOptions.setLog(LogOptions.LOG_DOWNLOAD_ONLY)
		val resolveEngine = new ResolveEngine(settings, eventManager, new SortEngine(settings))
		val resolveReport = resolveEngine.resolve(module, resolveOptions)
		if(resolveReport.hasError)
		{
			logExceptions(resolveReport)
			println(Set(resolveReport.getAllProblemMessages.toArray: _*).mkString(System.getProperty("line.separator")))
			throw new BootException("Error retrieving required libraries")
		}
	}
	/** Exceptions are logged to the update log file. */
	private def logExceptions(report: ResolveReport)
	{
		for(unresolved <- report.getUnresolvedDependencies)
		{
			val problem = unresolved.getProblem
			if(problem != null)
				problem.printStackTrace(logWriter)
        }
	}
	/** Retrieves resolved dependencies using the given target to determine the location to retrieve to. */
	private def retrieve(settings: IvySettings, eventManager: EventManager, module: ModuleDescriptor,  target: UpdateTarget.Value)
	{
		val retrieveOptions = new RetrieveOptions
		val retrieveEngine = new RetrieveEngine(settings, eventManager)
		val pattern =
			target match
			{
				// see BuildConfiguration
				case UpdateSbt => sbtRetrievePattern(sbtVersion)
				case UpdateScala => scalaRetrievePattern
			}
		retrieveEngine.retrieve(module.getModuleRevisionId, pattern, retrieveOptions);
	}
	/** Add the scala tools repositories and a URL resolver to download sbt from the Google code project.*/
	private def addResolvers(settings: IvySettings, scalaVersion: String,  target: UpdateTarget.Value)
	{
		val newDefault = new ChainResolver
		newDefault.setName("redefined-public")
		newDefault.add(localResolver(settings.getDefaultIvyUserDir.getAbsolutePath))
		newDefault.add(mavenLocal)
		newDefault.add(mavenMainResolver)
		target match
		{
			case UpdateSbt =>
				newDefault.add(sbtResolver(scalaVersion))
			case UpdateScala =>
				newDefault.add(mavenResolver("Scala-Tools Maven2 Repository", "http://scala-tools.org/repo-releases"))
				newDefault.add(scalaSnapshots(scalaVersion))
		}
		onDefaultRepositoryCacheManager(settings)(_.setUseOrigin(true))
		settings.addResolver(newDefault)
		settings.setDefaultResolver(newDefault.getName)
	}
	private def onDefaultRepositoryCacheManager(settings: IvySettings)(f: DefaultRepositoryCacheManager => Unit)
	{
		settings.getDefaultRepositoryCacheManager match
		{
			case manager: DefaultRepositoryCacheManager => f(manager)
			case _ => ()
		}
	}
	/** Uses the pattern defined in BuildConfiguration to download sbt from Google code.*/
	private def sbtResolver(scalaVersion: String) =
	{
		val pattern = sbtResolverPattern(scalaVersion)
		val resolver = new URLResolver
		resolver.setName("Sbt Repository")
		resolver.addIvyPattern(pattern)
		resolver.addArtifactPattern(pattern)
		resolver
	}
	private def mavenLocal = mavenResolver("Maven2 Local", "file://" + System.getProperty("user.home") + "/.m2/repository/")
	/** Creates a maven-style resolver.*/
	private def mavenResolver(name: String, root: String) =
	{
		val resolver = defaultMavenResolver(name)
		resolver.setRoot(root)
		resolver
	}
	private def scalaSnapshots(scalaVersion: String) =
	{
		val parts = """(\d+)\.(\d+)\.(\d+)-(.+)""".r.pattern.matcher(scalaVersion)
		if(parts.matches && parts.group(4) != "SNAPSHOT")
			snapshots( Seq(parts.group(1), parts.group(2), parts.group(3)).mkString(".") )
		else
			mavenResolver("Scala-Tools Maven2 Snapshots Repository", "http://scala-tools.org/repo-snapshots")
	}
	private def snapshots(baseVersion: String) =
	{
		val resolver = new URLResolver
		resolver.setName("Scala-Tools Maven2 Snapshots Repository")
		val base = "http://scala-tools.org/repo-snapshots/"
		resolver.setM2compatible(true)
		resolver.addArtifactPattern(base + "[organisation]/[module]/" + baseVersion + "-SNAPSHOT/[artifact]-[revision](-[classifier]).[ext]")
		resolver
	}
	/** Creates a resolver for Maven Central.*/
	private def mavenMainResolver = defaultMavenResolver("Maven Central")
	/** Creates a maven-style resolver with the default root.*/
	private def defaultMavenResolver(name: String) =
	{
		val resolver = new IBiblioResolver
		resolver.setName(name)
		resolver.setM2compatible(true)
		resolver
	}
	private def localResolver(ivyUserDirectory: String) =
	{
		val localIvyRoot = ivyUserDirectory + "/local"
		val artifactPattern = localIvyRoot + "/" + LocalArtifactPattern
		val ivyPattern = localIvyRoot + "/" + LocalIvyPattern
		val resolver = new FileSystemResolver
		resolver.setName(LocalIvyName)
		resolver.addIvyPattern(ivyPattern)
		resolver.addArtifactPattern(artifactPattern)
		resolver
	}
	/** Logs the given message to a file and to the console. */
	private def log(msg: String) =
	{
		try { logWriter.println(msg) }
		catch { case e: Exception => System.err.println("Error writing to update log file: " + e.toString) }
		println(msg)
	}
}
/** A custom logger for Ivy to ignore the messages about not finding classes
* intentionally filtered using proguard. */
private final class SbtIvyLogger(logWriter: PrintWriter) extends DefaultMessageLogger(Message.MSG_INFO) with NotNull
{
	private val ignorePrefix = "impossible to define"
	override def log(msg: String, level: Int)
	{
		logWriter.println(msg)
		if(level <= getLevel && msg != null && !msg.startsWith(ignorePrefix))
			System.out.println(msg)
	}
	override def rawlog(msg: String, level: Int) { log(msg, level) }
}