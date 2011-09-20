/* sbt -- Simple Build Tool
 * Copyright 2009, 2010, 2011  Mark Harrah
 */
package xsbt.boot

import Pre._
import java.io.{File, FileWriter, PrintWriter, Writer}
import java.util.concurrent.Callable
import java.util.regex.Pattern
import java.util.Properties

import org.apache.ivy.{core, plugins, util, Ivy}
import core.LogOptions
import core.cache.DefaultRepositoryCacheManager
import core.event.EventManager
import core.module.id.{ArtifactId, ModuleId, ModuleRevisionId}
import core.module.descriptor.{Configuration => IvyConfiguration, DefaultDependencyArtifactDescriptor, DefaultDependencyDescriptor, DefaultModuleDescriptor, ModuleDescriptor}
import core.module.descriptor.{Artifact => IArtifact, DefaultExcludeRule, ExcludeRule}
import core.report.ResolveReport
import core.resolve.{ResolveEngine, ResolveOptions}
import core.retrieve.{RetrieveEngine, RetrieveOptions}
import core.sort.SortEngine
import core.settings.IvySettings
import plugins.matcher.{ExactPatternMatcher, PatternMatcher}
import plugins.resolver.{ChainResolver, FileSystemResolver, IBiblioResolver, URLResolver}
import util.{DefaultMessageLogger, filter, Message, MessageLoggerEngine, url}
import filter.Filter
import url.CredentialsStore

import BootConfiguration._

sealed trait UpdateTarget { def tpe: String; def classifiers: List[String] }
final class UpdateScala(val classifiers: List[String]) extends UpdateTarget { def tpe = "scala" }
final class UpdateApp(val id: Application, val classifiers: List[String]) extends UpdateTarget { def tpe = "app" }

final class UpdateConfiguration(val bootDirectory: File, val ivyHome: Option[File], val scalaVersion: String, val repositories: List[xsbti.Repository])

/** Ensures that the Scala and application jars exist for the given versions or else downloads them.*/
final class Update(config: UpdateConfiguration)
{
	import config.{bootDirectory, ivyHome, repositories, scalaVersion}
	bootDirectory.mkdirs

	private def logFile = new File(bootDirectory, UpdateLogName)
	private val logWriter = new PrintWriter(new FileWriter(logFile))

	private def addCredentials()
	{
		val optionProps = 
			Option(System.getProperty("sbt.boot.credentials")) orElse 
			Option(System.getenv("SBT_CREDENTIALS")) map ( path =>
				ResolveValues.readProperties(new File(path)) 
			)
		optionProps foreach extractCredentials("realm","host","user","password")
		extractCredentials("sbt.boot.realm","sbt.boot.host","sbt.boot.user","sbt.boot.password")(System.getProperties)
	}
	private def extractCredentials(keys: (String,String,String,String))(props: Properties) {
		val List(realm, host, user, password) = keys.productIterator.map(key => props.getProperty(key.toString)).toList
		if (realm != null && host != null && user != null && password != null)
			CredentialsStore.INSTANCE.addCredentials(realm, host, user, password)
        }
	private lazy val settings =
	{
		addCredentials()
		val settings = new IvySettings
		ivyHome foreach settings.setDefaultIvyUserDir
		addResolvers(settings)
		settings.setVariable("ivy.checksums", "sha1,md5")
		settings.setDefaultConflictManager(settings.getConflictManager(ConflictManagerName))
		settings.setBaseDir(bootDirectory)
		settings.setVariable("scala", scalaVersion)
		settings
	}
	private lazy val ivy =
	{
		val ivy = new Ivy() { private val loggerEngine = new SbtMessageLoggerEngine; override def getLoggerEngine = loggerEngine }
		ivy.setSettings(settings)
		ivy.bind()
		ivy
	}
	// should be the same file as is used in the Ivy module
	private lazy val ivyLockFile = new File(settings.getDefaultIvyUserDir, ".sbt.ivy.lock")

	/** The main entry point of this class for use by the Update module.  It runs Ivy */
	def apply(target: UpdateTarget, reason: String): Boolean =
	{
		Message.setDefaultLogger(new SbtIvyLogger(logWriter))
		val action = new Callable[Boolean] { def call =  lockedApply(target, reason) }
		Locks(ivyLockFile, action)
	}
	private def lockedApply(target: UpdateTarget, reason: String) =
	{
		ivy.pushContext()
		try { update(target, reason); true }
		catch
		{
			case e: Exception =>
				e.printStackTrace(logWriter)
				log(e.toString)
				System.out.println("  (see " + logFile + " for complete log)")
				false
		}
		finally
		{
			logWriter.close()
			ivy.popContext()
		}
	}
	/** Runs update for the specified target (updates either the scala or appliciation jars for building the project) */
	private def update(target: UpdateTarget, reason: String)
	{
		import IvyConfiguration.Visibility.PUBLIC
		// the actual module id here is not that important
		val moduleID = new DefaultModuleDescriptor(createID(SbtOrg, "boot-" + target.tpe, "1.0"), "release", null, false)
		moduleID.setLastModified(System.currentTimeMillis)
		moduleID.addConfiguration(new IvyConfiguration(DefaultIvyConfiguration, PUBLIC, "", new Array(0), true, null))
		// add dependencies based on which target needs updating
		target match
		{
			case u: UpdateScala =>
				addDependency(moduleID, ScalaOrg, CompilerModuleName, scalaVersion, "default;optional(default)", u.classifiers)
				addDependency(moduleID, ScalaOrg, LibraryModuleName, scalaVersion, "default", u.classifiers)
				excludeJUnit(moduleID)
				System.out.println("Getting Scala " + scalaVersion + " " + reason + "...")
			case u: UpdateApp =>
				val app = u.id
				val resolvedName = if(app.crossVersioned) app.name + "_" + scalaVersion else app.name
				addDependency(moduleID, app.groupID, resolvedName, app.getVersion, "default(compile)", u.classifiers)
				excludeScala(moduleID)
				System.out.println("Getting " + app.groupID + " " + resolvedName + " " + app.getVersion + " " + reason + "...")
		}
		update(moduleID, target)
	}
	/** Runs the resolve and retrieve for the given moduleID, which has had its dependencies added already. */
	private def update(moduleID: DefaultModuleDescriptor,  target: UpdateTarget)
	{
		val eventManager = new EventManager
		resolve(eventManager, moduleID)
		retrieve(eventManager, moduleID, target)
	}
	private def createID(organization: String, name: String, revision: String) =
		ModuleRevisionId.newInstance(organization, name, revision)
	/** Adds the given dependency to the default configuration of 'moduleID'. */
	private def addDependency(moduleID: DefaultModuleDescriptor, organization: String, name: String, revision: String, conf: String, classifiers: List[String])
	{
		val dep = new DefaultDependencyDescriptor(moduleID, createID(organization, name, revision), false, false, true)
		for(c <- conf.split(";"))
			dep.addDependencyConfiguration(DefaultIvyConfiguration, c)
		for(classifier <- classifiers)
			addClassifier(dep, name, classifier)
		moduleID.addDependency(dep)
	}
	private def addClassifier(dep: DefaultDependencyDescriptor, name: String, classifier: String)
	{
		val extraMap = new java.util.HashMap[String,String]
		if(!classifier.isEmpty)
			extraMap.put("e:classifier", classifier)
		val ivyArtifact = new DefaultDependencyArtifactDescriptor(dep, name, artifactType(classifier), "jar", null, extraMap)
		for(conf <- dep.getModuleConfigurations)
			dep.addDependencyArtifact(conf, ivyArtifact)
	}
	private def excludeJUnit(module: DefaultModuleDescriptor): Unit = module.addExcludeRule(excludeRule(JUnitName, JUnitName))
	private def excludeScala(module: DefaultModuleDescriptor)
	{
		def excludeScalaJar(name: String): Unit = module.addExcludeRule(excludeRule(ScalaOrg, name))
		excludeScalaJar(LibraryModuleName)
		excludeScalaJar(CompilerModuleName)
	}
	private def excludeRule(organization: String, name: String): ExcludeRule =
	{
		val artifact = new ArtifactId(ModuleId.newInstance(organization, name), "*", "*", "*")
		val rule = new DefaultExcludeRule(artifact, ExactPatternMatcher.INSTANCE, java.util.Collections.emptyMap[AnyRef,AnyRef])
		rule.addConfiguration(DefaultIvyConfiguration)
		rule
	}
	private def resolve(eventManager: EventManager, module: ModuleDescriptor)
	{
		val resolveOptions = new ResolveOptions
		  // this reduces the substantial logging done by Ivy, including the progress dots when downloading artifacts
		resolveOptions.setLog(LogOptions.LOG_DOWNLOAD_ONLY)
		resolveOptions.setCheckIfChanged(false)
		val resolveEngine = new ResolveEngine(settings, eventManager, new SortEngine(settings))
		val resolveReport = resolveEngine.resolve(module, resolveOptions)
		if(resolveReport.hasError)
		{
			logExceptions(resolveReport)
			val seen = new java.util.LinkedHashSet[Any]
			seen.addAll(resolveReport.getAllProblemMessages)
			System.out.println(seen.toArray.mkString(System.getProperty("line.separator")))
			error("Error retrieving required libraries")
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
	private final class ArtifactFilter(f: IArtifact => Boolean) extends Filter {
		def accept(o: Any) = o match { case a: IArtifact => f(a); case _ => false }
	}
	/** Retrieves resolved dependencies using the given target to determine the location to retrieve to. */
	private def retrieve(eventManager: EventManager, module: ModuleDescriptor,  target: UpdateTarget)
	{
		val retrieveOptions = new RetrieveOptions
		retrieveOptions.setArtifactFilter(new ArtifactFilter(a => "jar" == a.getType && a.getExtraAttribute("classifier") == null))
		val retrieveEngine = new RetrieveEngine(settings, eventManager)
		val pattern =
			target match
			{
				case _: UpdateScala => scalaRetrievePattern
				case u: UpdateApp => appRetrievePattern(u.id.toID)
			}
		retrieveEngine.retrieve(module.getModuleRevisionId, baseDirectoryName(scalaVersion) + "/" + pattern, retrieveOptions)
	}
	/** Add the scala tools repositories and a URL resolver to download sbt from the Google code project.*/
	private def addResolvers(settings: IvySettings)
	{
		val newDefault = new ChainResolver {
			override def locate(artifact: IArtifact) =
				if(hasImplicitClassifier(artifact)) null else super.locate(artifact)
		}
		newDefault.setName("redefined-public")
		if(repositories.isEmpty) error("No repositories defined.")
		for(repo <- repositories if includeRepo(repo))
			newDefault.add(toIvyRepository(settings, repo))
		configureCache(settings)
		settings.addResolver(newDefault)
		settings.setDefaultResolver(newDefault.getName)
	}
	// infrastructure is needed to avoid duplication between this class and the ivy/ subproject
	private def hasImplicitClassifier(artifact: IArtifact): Boolean =
	{
		import collection.JavaConversions._
		artifact.getQualifiedExtraAttributes.keys.exists(_.asInstanceOf[String] startsWith "m:")
	}
	// exclude the local Maven repository for Scala -SNAPSHOTs
	private def includeRepo(repo: xsbti.Repository) = !(Repository.isMavenLocal(repo) && isSnapshot(scalaVersion) )
	private def isSnapshot(scalaVersion: String) = scalaVersion.endsWith(Snapshot)
	private[this] val Snapshot = "-SNAPSHOT"
	private[this] val ChangingPattern = ".*" + Snapshot
	private[this] val ChangingMatcher = PatternMatcher.REGEXP
	private def configureCache(settings: IvySettings)
	{
		val manager = new DefaultRepositoryCacheManager("default-cache", settings, settings.getDefaultRepositoryCacheBasedir())
		manager.setUseOrigin(true)
		manager.setChangingMatcher(ChangingMatcher)
		manager.setChangingPattern(ChangingPattern)
		settings.addRepositoryCacheManager(manager)
		settings.setDefaultRepositoryCacheManager(manager)
	}
	private def toIvyRepository(settings: IvySettings, repo: xsbti.Repository) =
	{
		import xsbti.Predefined._
		repo match
		{
			case m: xsbti.MavenRepository => mavenResolver(m.id, m.url.toString)
			case i: xsbti.IvyRepository => urlResolver(i.id, i.url.toString, i.ivyPattern, i.artifactPattern)
			case p: xsbti.PredefinedRepository => p.id match {
				case Local => localResolver(settings.getDefaultIvyUserDir.getAbsolutePath)
				case MavenLocal => mavenLocal
				case MavenCentral => mavenMainResolver
				case ScalaToolsReleases => mavenResolver("Scala-Tools Maven2 Repository", "http://scala-tools.org/repo-releases")
				case ScalaToolsSnapshots => scalaSnapshots(scalaVersion)
			}
		}
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
	private def urlResolver(id: String, base: String, ivyPattern: String, artifactPattern: String) =
	{
		val resolver = new URLResolver
		resolver.setName(id)
		resolver.addIvyPattern(adjustPattern(base, ivyPattern))
		resolver.addArtifactPattern(adjustPattern(base, artifactPattern))
		resolver
	}
	private def adjustPattern(base: String, pattern: String): String =
		(if(base.endsWith("/") || base.isEmpty) base else (base + "/") ) + pattern
	private def mavenLocal = mavenResolver("Maven2 Local", "file://" + System.getProperty("user.home") + "/.m2/repository/")
	/** Creates a maven-style resolver.*/
	private def mavenResolver(name: String, root: String) =
	{
		val resolver = defaultMavenResolver(name)
		resolver.setRoot(root)
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
		val resolver = new FileSystemResolver
		resolver.setName(LocalIvyName)
		resolver.addIvyPattern(localIvyRoot + "/" + LocalIvyPattern)
		resolver.addArtifactPattern(localIvyRoot + "/" + LocalArtifactPattern)
		resolver
	}
	private val SnapshotPattern = Pattern.compile("""(\d+).(\d+).(\d+)-(\d{8})\.(\d{6})-(\d+|\+)""")
	private def scalaSnapshots(scalaVersion: String) =
	{
		val m = SnapshotPattern.matcher(scalaVersion)
		if(m.matches)
		{
			val base = List(1,2,3).map(m.group).mkString(".")
			val pattern = "http://scala-tools.org/repo-snapshots/[organization]/[module]/" + base + "-SNAPSHOT/[artifact]-[revision](-[classifier]).[ext]"

			val resolver = new URLResolver
			resolver.setName("Scala Tools Snapshots")
			resolver.setM2compatible(true)
			resolver.addArtifactPattern(pattern)
			resolver
		}
		else
			mavenResolver("Scala-Tools Maven2 Snapshots Repository", "http://scala-tools.org/repo-snapshots")
	}
	/** Logs the given message to a file and to the console. */
	private def log(msg: String) =
	{
		try { logWriter.println(msg) }
		catch { case e: Exception => System.err.println("Error writing to update log file: " + e.toString) }
		System.out.println(msg)
	}
}

import SbtIvyLogger.{acceptError, acceptMessage}

/** A custom logger for Ivy to ignore the messages about not finding classes
* intentionally filtered using proguard and about 'unknown resolver'. */
private final class SbtIvyLogger(logWriter: PrintWriter) extends DefaultMessageLogger(Message.MSG_INFO)
{
	override def log(msg: String, level: Int)
	{
		logWriter.println(msg)
		if(level <= getLevel && acceptMessage(msg))
			System.out.println(msg)
	}
	override def rawlog(msg: String, level: Int) { log(msg, level) }
	/** This is a hack to filter error messages about 'unknown resolver ...'. */
	override def error(msg: String) = if(acceptError(msg)) super.error(msg)
}
private final class SbtMessageLoggerEngine extends MessageLoggerEngine
{
	/** This is a hack to filter error messages about 'unknown resolver ...'. */
	override def error(msg: String) = if(acceptError(msg)) super.error(msg)
}
private object SbtIvyLogger
{
	val IgnorePrefix = "impossible to define"
	val UnknownResolver = "unknown resolver"
	def acceptError(msg: String) = acceptMessage(msg) && !msg.startsWith(UnknownResolver)
	def acceptMessage(msg: String) = (msg ne null) && !msg.startsWith(IgnorePrefix)
}
