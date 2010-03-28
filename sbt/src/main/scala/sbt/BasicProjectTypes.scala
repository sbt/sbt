/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package sbt

import scala.xml.NodeSeq
import StringUtilities.{appendable,nonEmpty}
import BasicManagedProject._

/** A project that provides a classpath. */
trait ClasspathProject extends Project
{
	/** The local classpath for this project.*/
	def projectClasspath(config: Configuration): PathFinder

	/** Returns the classpath of this project and the classpaths of all dependencies for the
	* given configuration.  Specifically, this concatentates projectClasspath(config) for all
	* projects of type ClasspathProject in topologicalSort. */
	def fullClasspath(config: Configuration): PathFinder =
		Path.lazyPathFinder
		{
			val set = new wrap.MutableSetWrapper(new java.util.LinkedHashSet[Path])
			for(project <- topologicalSort)
			{
				project match
				{
					case sp: ClasspathProject => set ++= sp.projectClasspath(config).get
					case _ => ()
				}
			}
			set.toList
		}
	/* Filter used to select dependencies for the classpath from managed and unmanaged directories.
	* By default, it explicitly filters (x)sbt-launch(er)-<version>.jar, since it contains minified versions of various classes.*/
   def classpathFilter: FileFilter = "*.jar" - "*sbt-launch*.jar"
}
trait BasicDependencyProject extends BasicManagedProject with UnmanagedClasspathProject
{
	/** This returns the classpath for only this project for the given configuration.*/
	def projectClasspath(config: Configuration) = fullUnmanagedClasspath(config) +++ managedClasspath(config)
}
/** A project that provides a directory in which jars can be manually managed.*/
trait UnmanagedClasspathProject extends ClasspathProject
{
	/** The location of the manually managed (unmanaged) dependency directory.*/
	def dependencyPath: Path
	/** The classpath containing all jars in the unmanaged directory. */
	def unmanagedClasspath: PathFinder =
	{
		val base = descendents(dependencyPath, classpathFilter)
		if(scratch)
			base +++ (info.projectPath * classpathFilter)
		else
			base
	}
	/** The classpath containing all unmanaged classpath elements for the given configuration. This typically includes
	* at least 'unmanagedClasspath'.*/
	def fullUnmanagedClasspath(config: Configuration): PathFinder
}

trait IvyTasks extends Project
{
	def ivyTask(action: => Unit) =
		task
		{
			try { action; None }
			catch {
				case e: ResolveException =>
					log.error(e.toString)
					Some(e.toString)
				case e: Exception =>
					log.trace(e)
					log.error(e.toString)
					Some(e.toString)
			}
		}
	def updateTask(module: => IvySbt#Module, configuration: => UpdateConfiguration) =
		ivyTask { IvyActions.update(module, configuration) }

	def publishTask(module: => IvySbt#Module, publishConfiguration: => PublishConfiguration) =
		ivyTask
		{
			val publishConfig = publishConfiguration
			import publishConfig._
			val deliveredIvy = if(publishIvy) Some(deliveredPattern) else None
			IvyActions.publish(module, resolverName, srcArtifactPatterns, deliveredIvy, configurations)
		}
	def deliverTask(module: => IvySbt#Module, deliverConfiguration: => PublishConfiguration, quiet: Boolean) =
		ivyTask
		{
			val deliverConfig = deliverConfiguration
			import deliverConfig._
			IvyActions.deliver(module, status, deliveredPattern, extraDependencies, configurations, quiet)
		}
	def makePomTask(module: => IvySbt#Module, output: => Path, extraDependencies: => Iterable[ModuleID], pomExtra: => NodeSeq, configurations: => Option[Iterable[Configuration]]) =
		ivyTask { IvyActions.makePom(module, extraDependencies, configurations, pomExtra, output asFile) }

	def installTask(module: IvySbt#Module, from: Resolver, to: Resolver) =
		ivyTask { IvyActions.install(module, from.name, to.name) }
		
	def cleanCacheTask(ivySbt: => IvySbt) =
		ivyTask { IvyActions.cleanCache(ivySbt) }

	def cleanLibTask(managedDependencyPath: Path) = 
		task { FileUtilities.clean(managedDependencyPath.get, log) }
}

/** A project that provides automatic dependency management.*/
trait ManagedProject extends ClasspathProject with IvyTasks
{
	/** This is the public ID of the project (used for publishing, for example) */
	def moduleID: String = normalizedName + appendable(crossScalaVersionString)
	/** This is the full public ID of the project (used for publishing, for example) */
	def projectID: ModuleID = ModuleID(organization, moduleID, version.toString).artifacts(artifacts.toSeq : _*)

	/** This is the default name for artifacts (such as jars) without any version string.*/
	def artifactID = moduleID
	/** This is the default name for artifacts (such as jars) including the version string.*/
	def artifactBaseName = artifactID + "-" + version.toString
	def artifacts: Iterable[Artifact]

	def managedDependencyPath: Path
	/** The managed classpath for the given configuration.  This can be overridden to add jars from other configurations
	* so that the Ivy 'extends' mechanism is not required.  That way, the jars are only copied to one configuration.*/
	def managedClasspath(config: Configuration): PathFinder = configurationClasspath(config)
	/** All dependencies in the given configuration. */
	final def configurationClasspath(config: Configuration): PathFinder = descendents(configurationPath(config), classpathFilter)

	/** The base path to which dependencies in configuration 'config' are downloaded.*/
	def configurationPath(config: Configuration): Path = managedDependencyPath / config.toString

	import StringUtilities.nonEmpty
	implicit def toGroupID(groupID: String): GroupID =
	{
		nonEmpty(groupID, "Group ID")
		new GroupID(groupID, buildScalaVersion)
	}
	implicit def toRepositoryName(name: String): RepositoryName =
	{
		nonEmpty(name, "Repository name")
		new RepositoryName(name)
	}
	implicit def moduleIDConfigurable(m: ModuleID): ModuleIDConfigurable =
	{
		require(m.configurations.isEmpty, "Configurations already specified for module " + m)
		new ModuleIDConfigurable(m)
	}

	/** Creates a new configuration with the given name.*/
	def config(name: String) = new Configuration(name)
}
/** This class groups required configuration for the deliver and publish tasks. */
trait PublishConfiguration extends NotNull
{
	/** The name of the resolver to which publishing should be done.*/
	def resolverName: String
	/** The Ivy pattern used to determine the delivered Ivy file location.  An example is
	* (outputPath / "[artifact]-[revision].[ext]").relativePath. */
	def deliveredPattern: String
	/** Ivy patterns used to find artifacts for publishing.  An example pattern is
	* (outputPath / "[artifact]-[revision].[ext]").relativePath */
	def srcArtifactPatterns: Iterable[String]
	/** Additional dependencies to include for delivering/publishing only.  These are typically dependencies on
	* subprojects. */
	def extraDependencies: Iterable[ModuleID]
	/** The status to use when delivering or publishing.  This might be "release" or "integration" or another valid Ivy status. */
	def status: String
	/**  The configurations to include in the publish/deliver action: specify none for all configurations. */
	def configurations: Option[Iterable[Configuration]]
	/** True if the Ivy file should be published. */
	def publishIvy: Boolean
}
object ManagedStyle extends Enumeration
{
	val Maven, Ivy, Auto = Value
}
import ManagedStyle.{Auto, Ivy, Maven, Value => ManagedType}
trait BasicManagedProject extends ManagedProject with ReflectiveManagedProject with BasicDependencyPaths
{
	def ivyUpdateConfiguration =  new UpdateConfiguration(managedDependencyPath.asFile, outputPattern, true/*sync*/, true/*quiet*/)

	def ivyRepositories: Seq[Resolver] =
	{
		val repos = repositories.toSeq
		if(repos.isEmpty) Nil else Resolver.withDefaultResolvers(repos)
	}
	def otherRepositories: Seq[Resolver] = defaultPublishRepository.toList
	def ivyValidate = true
	def ivyScala: Option[IvyScala] = Some(new IvyScala(buildScalaVersion, checkScalaInConfigurations, checkExplicitScalaDependencies, filterScalaJars))
	def ivyCacheDirectory: Option[Path] = None
	
	def ivyPaths: IvyPaths = new IvyPaths(info.projectPath.asFile, ivyCacheDirectory.map(_.asFile))
	def inlineIvyConfiguration = new InlineIvyConfiguration(ivyPaths, ivyRepositories.toSeq, otherRepositories, moduleConfigurations.toSeq, Some(info.launcher.globalLock), log)
	def ivyConfiguration: IvyConfiguration =
	{
		val in = inlineIvyConfiguration
		def adapt(c: IvyConfiguration): IvyConfiguration = c.withBase(in.baseDirectory)
		def parentIvyConfiguration(default: IvyConfiguration)(p: Project) = p match { case b: BasicManagedProject => adapt(b.ivyConfiguration); case _ => default }
		if(in.resolvers.isEmpty)
		{
			 if(in.moduleConfigurations.isEmpty && in.otherResolvers.isEmpty)
			 {
				IvyConfiguration(in.paths, in.lock, in.log) match
				{
					case e: ExternalIvyConfiguration => e
					case i => info.parent map(parentIvyConfiguration(i)) getOrElse(i)
				}
			}
			else
				new InlineIvyConfiguration(in.paths, Resolver.withDefaultResolvers(Nil), in.otherResolvers, in.moduleConfigurations, in.lock, in.log)
		}
		else
			in
	}
	
	def moduleSettings: ModuleSettings = defaultModuleSettings
	def byIvyFile(path: Path): IvyFileConfiguration = new IvyFileConfiguration(path.asFile, ivyScala, ivyValidate)
	def byPom(path: Path): PomConfiguration = new PomConfiguration(path.asFile, ivyScala, ivyValidate)
	/** The settings that represent inline declarations.  The default settings combines the information
	* from 'ivyXML', 'projectID', 'repositories', ivyConfigurations, defaultConfiguration,
	* ivyScala, and 'libraryDependencies' and does not typically need to be be overridden. */
	def inlineSettings = new InlineConfiguration(projectID, withCompat, ivyXML, ivyConfigurations, defaultConfiguration, ivyScala, ivyValidate)
	/** Library dependencies with extra dependencies for compatibility*/
	private def withCompat =
	{
		val deps = libraryDependencies
		deps ++ compatExtra(deps)
	}
	/** Determines extra libraries needed for compatibility.  Currently, this is the compatibility test framework. */
	private def compatExtra(deps: Set[ModuleID]) =
		if(isScala27 && deps.exists(requiresCompat)) { log.debug("Using compatibility implementation of test interface."); compatTestFramework } else Nil
	/** True if the given dependency requires the compatibility test framework. */
	private def requiresCompat(m: ModuleID) =
	{
		def nameMatches(name: String, id: String) = name == id || name.startsWith(id + "_2.7.")
		
		(nameMatches(m.name, "scalacheck") && Set("1.5", "1.6").contains(m.revision)) ||
		(nameMatches(m.name, "specs") && Set("1.6.0", "1.6.1").contains(m.revision)) ||
		(nameMatches(m.name,  "scalatest") && m.revision == "1.0")
	}
	/** Extra dependencies to add if a dependency on an older test framework (one released before the uniform test interface) is declared.
	* This is the compatibility test framework by default.*/
	def compatTestFramework = Set("org.scala-tools.sbt" %% "test-compat" % "0.4.1" % "test")
	
	def defaultModuleSettings: ModuleSettings =
	{
		val in = inlineSettings
		if(in.configurations.isEmpty)
		{
			if(in.dependencies.isEmpty && in.ivyXML.isEmpty && (in.module.explicitArtifacts.size <= 1) && in.configurations.isEmpty)
				externalSettings
			else if(useDefaultConfigurations)
				in withConfigurations ( Configurations.defaultMavenConfigurations )
			else
				in
		}
		else
			in
	}
	def externalSettings = ModuleSettings(ivyScala, ivyValidate, projectID)(info.projectPath.asFile, log)
	
	def ivySbt: IvySbt = new IvySbt(ivyConfiguration)
	def ivyModule: IvySbt#Module = newIvyModule(moduleSettings)
	def newIvyModule(moduleSettings: ModuleSettings): IvySbt#Module =
	{
		val i = ivySbt
		new i.Module(moduleSettings)
	}
	

	/** The pattern for Ivy to use when retrieving dependencies into the local project.  Classpath management
	* depends on the first directory being [conf] and the extension being [ext].*/
	def outputPattern = "[conf]/[artifact](-[revision])(-[classifier]).[ext]"
	/** Override this to specify the publications, configurations, and/or dependencies sections of an Ivy file.
	* See http://code.google.com/p/simple-build-tool/wiki/LibraryManagement for details.*/
	def ivyXML: NodeSeq = NodeSeq.Empty
	def pomExtra: NodeSeq = NodeSeq.Empty

	override def ivyConfigurations: Iterable[Configuration] =
	{
		val reflective = super.ivyConfigurations
		val extra = extraDefaultConfigurations
		if(useDefaultConfigurations)
		{
			if(reflective.isEmpty && extra.isEmpty)
				Nil
			else
				Configurations.removeDuplicates(Configurations.defaultMavenConfigurations ++ reflective ++ extra)
		}
		else
			reflective ++ extra
	}
	def extraDefaultConfigurations: List[Configuration] = Nil
	def useIntegrationTestConfiguration = false
	def defaultConfiguration: Option[Configuration] = Some(Configurations.DefaultConfiguration(useDefaultConfigurations))
	def useMavenConfigurations = true // TODO: deprecate after going through a minor version series to verify that this works ok
	def useDefaultConfigurations = useMavenConfigurations
	def managedStyle: ManagedType =
		info.parent match
		{
			case Some(m: BasicManagedProject) => m.managedStyle
			case _ => Auto
		}
	protected implicit final val defaultPatterns: Patterns =
	{
		managedStyle match
		{
			case Maven => Resolver.mavenStylePatterns
			case Ivy => Resolver.ivyStylePatterns
			case Auto => Resolver.defaultPatterns
		}
	}
	
	def updateModuleSettings = moduleSettings
	def updateIvyModule = newIvyModule(updateModuleSettings)
	def deliverModuleSettings = moduleSettings.noScala
	def deliverIvyModule = newIvyModule(deliverModuleSettings)
	def publishModuleSettings = deliverModuleSettings
	def publishIvyModule = newIvyModule(publishModuleSettings)
	/** True if the 'provided' configuration should be included on the 'compile' classpath.  The default value is true.*/
	def includeProvidedWithCompile = true
	/** True if the default implicit extensions should be used when determining classpaths.  The default value is true. */
	def defaultConfigurationExtensions = true
	/** If true, verify that explicit dependencies on Scala libraries use the same version as scala.version. */
	def checkExplicitScalaDependencies = true
	/** If true, filter dependencies on scala-library and scala-compiler. This is true by default to avoid conflicts with
	* the jars provided by sbt.  You can set this to false to download these jars.  Overriding checkScalaInConfigurations might
	* be more appropriate, however.*/
	def filterScalaJars = true
	/** The configurations to check/filter.*/
	def checkScalaInConfigurations: Iterable[Configuration] = ivyConfigurations
	def defaultPublishRepository: Option[Resolver] =
	{
		reflectiveRepositories.get(PublishToName) orElse
		info.parent.flatMap
			{
				case managed: BasicManagedProject => managed.defaultPublishRepository
				case _ => None
			}
	}
	/** Includes the Provided configuration on the Compile classpath, the Compile configuration on the Runtime classpath,
	* and Compile and Runtime on the Test classpath.  Including Provided can be disabled by setting
	* includeProvidedWithCompile to false.  Including Compile and Runtime can be disabled by setting
	* defaultConfigurationExtensions to false.*/
	override def managedClasspath(config: Configuration) =
	{
		import Configurations.{Compile, CompilerPlugin, Default, Provided, Runtime, Test}
		val baseClasspath = configurationClasspath(config)
		config match
		{
			case Compile =>
				val baseCompileClasspath = baseClasspath +++ managedClasspath(Default)
				if(includeProvidedWithCompile)
					baseCompileClasspath +++ managedClasspath(Provided)
				else
					baseCompileClasspath
			case Runtime if defaultConfigurationExtensions => baseClasspath +++ managedClasspath(Compile)
			case Test if defaultConfigurationExtensions => baseClasspath +++ managedClasspath(Runtime)
			case _ => baseClasspath
		}
	}

	protected def updateAction = updateTask(updateIvyModule, ivyUpdateConfiguration) describedAs UpdateDescription
	protected def cleanLibAction = cleanLibTask(managedDependencyPath) describedAs CleanLibDescription
	protected def cleanCacheAction = cleanCacheTask(ivySbt) describedAs CleanCacheDescription

	protected def deliverProjectDependencies: Iterable[ModuleID] =
	{
		val interDependencies = new scala.collection.mutable.ListBuffer[ModuleID]
		dependencies.foreach(dep => dep match { case mp: ManagedProject => interDependencies += mp.projectID; case _ => () })
		if(filterScalaJars)
			interDependencies ++= deliverScalaDependencies
		interDependencies.readOnly
	}
	protected def deliverScalaDependencies: Iterable[ModuleID] = Nil
	protected def makePomAction = makePomTask(deliverIvyModule, pomPath, deliverProjectDependencies, pomExtra, None)
	protected def deliverLocalAction = deliverTask(deliverIvyModule, publishLocalConfiguration, true /*quiet*/)
	protected def publishLocalAction =
	{
		val dependencies = deliverLocal :: publishPomDepends
		publishTask(publishIvyModule, publishLocalConfiguration) dependsOn(dependencies : _*)
	}
	protected def publishLocalConfiguration = new DefaultPublishConfiguration("local", "release", true)
	protected def deliverAction = deliverTask(deliverIvyModule, publishConfiguration, true)
	protected def publishAction =
	{
		val dependencies = deliver :: publishPomDepends
		publishTask(publishIvyModule, publishConfiguration) dependsOn(dependencies : _*)
	}
	private def publishPomDepends = if(managedStyle == Maven) makePom :: Nil else Nil
	protected def publishConfiguration =
	{
		val repository = defaultPublishRepository.getOrElse(error("Repository to publish to not specified."))
		val publishIvy = managedStyle != Maven
		new DefaultPublishConfiguration(repository, "release", publishIvy)
	}
	protected class DefaultPublishConfiguration(val resolverName: String, val status: String, val publishIvy: Boolean) extends PublishConfiguration
	{
		def this(resolver: Resolver, status: String, publishIvy: Boolean) = this(resolver.name, status, publishIvy)
		def this(resolverName: String, status: String) = this(resolverName, status, true)
		def this(resolver: Resolver, status: String) = this(resolver.name, status)

		protected def deliveredPathPattern = outputPath / "[artifact]-[revision](-[classifier]).[ext]"
		def deliveredPattern = deliveredPathPattern.relativePath
		def srcArtifactPatterns: Iterable[String] =
		{
			val pathPatterns =
				(outputPath / "[artifact]-[revision]-[type](-[classifier]).[ext]") ::
				(outputPath / "[artifact]-[revision](-[classifier]).[ext]") ::
				Nil
			pathPatterns.map(_.relativePath)
		}
		def extraDependencies: Iterable[ModuleID] = deliverProjectDependencies
		/**  The configurations to include in the publish/deliver action: specify none for all public configurations. */
		def configurations: Option[Iterable[Configuration]] = None
	}

	def packageToPublishActions: Seq[ManagedTask] = Nil

	private[this] def depMap[T](f: BasicManagedProject => T) =
		topologicalSort.dropRight(1).flatMap { case m: BasicManagedProject => f(m) :: Nil; case _ => Nil }

	lazy val update = updateAction
	lazy val makePom = makePomAction dependsOn(packageToPublishActions : _*)
	lazy val cleanLib = cleanLibAction
	lazy val cleanCache = cleanCacheAction
	// deliver must run after its dependencies' `publish` so that the artifacts produced by the dependencies can be resolved
	//  (deliver requires a resolve first)
	lazy val deliverLocal: Task = deliverLocalAction dependsOn((depMap(_.publishLocal) ++ packageToPublishActions) : _*)
	lazy val publishLocal: Task = publishLocalAction
	lazy val deliver: Task = deliverAction dependsOn((depMap(_.publish) ++ packageToPublishActions)  : _*)
	lazy val publish: Task = publishAction
}

object BasicManagedProject
{
	val UpdateDescription =
		"Resolves and retrieves automatically managed dependencies."
	val CleanLibDescription =
		"Deletes the managed library directory."
	val CleanCacheDescription =
		"Deletes the cache of artifacts downloaded for automatically managed dependencies."

	val PublishToName = "publish-to"
	val RetrieveFromName = "retrieve-from"
}

class DefaultInstallProject(val info: ProjectInfo) extends InstallProject with MavenStyleScalaPaths with BasicDependencyProject
{
	def fullUnmanagedClasspath(config: Configuration) = unmanagedClasspath
	def dependencies = info.dependencies
}
trait InstallProject extends BasicManagedProject
{
	def installModuleSettings: ModuleSettings = moduleSettings.noScala
	def installIvyModule: IvySbt#Module = newIvyModule(installModuleSettings)
	
	lazy val install = installTask(installIvyModule, fromResolver, toResolver)
	def toResolver = reflectiveRepositories.get(PublishToName).getOrElse(error("No repository to publish to was specified"))
	def fromResolver = reflectiveRepositories.get(RetrieveFromName).getOrElse(error("No repository to retrieve from was specified"))
}

trait BasicDependencyPaths extends ManagedProject
{
	import BasicDependencyPaths._
	def dependencyDirectoryName = DefaultDependencyDirectoryName
	def managedDirectoryName = DefaultManagedDirectoryName
	def pomName = artifactBaseName + PomExtension
	def dependencyPath = path(dependencyDirectoryName)
	def managedDependencyPath = crossPath(managedDependencyRootPath)
	def managedDependencyRootPath: Path = managedDirectoryName
	def pomPath = outputPath / pomName
}
object BasicDependencyPaths
{
	val DefaultManagedDirectoryName = "lib_managed"
	val DefaultManagedSourceDirectoryName = "src_managed"
	val DefaultDependencyDirectoryName = "lib"
	val PomExtension = ".pom"
}

object StringUtilities
{
	def normalize(s: String) = s.toLowerCase.replaceAll("""\s+""", "-")
	def nonEmpty(s: String, label: String)
	{
		require(s.trim.length > 0, label + " cannot be empty.")
	}
	def appendable(s: String) = if(s.isEmpty) "" else "_" + s
}
final class GroupID private[sbt] (groupID: String, scalaVersion: String) extends NotNull
{
	def % (artifactID: String) = groupArtifact(artifactID)
	def %% (artifactID: String) =
	{
		require(!scalaVersion.isEmpty, "Cannot use %% when the sbt launcher is not used.")
		groupArtifact(artifactID + appendable(scalaVersion))
	}
	private def groupArtifact(artifactID: String) =
	{
		nonEmpty(artifactID, "Artifact ID")
		new GroupArtifactID(groupID, artifactID)
	}
}
final class GroupArtifactID private[sbt] (groupID: String, artifactID: String) extends NotNull
{
	def % (revision: String): ModuleID =
	{
		nonEmpty(revision, "Revision")
		ModuleID(groupID, artifactID, revision, None)
	}
}
final class ModuleIDConfigurable private[sbt] (moduleID: ModuleID) extends NotNull
{
	def % (configurations: String): ModuleID =
	{
		nonEmpty(configurations, "Configurations")
		import moduleID._
		ModuleID(organization, name, revision, Some(configurations))
	}
}
final class RepositoryName private[sbt] (name: String) extends NotNull
{
	def at (location: String) =
	{
		nonEmpty(location, "Repository location")
		new MavenRepository(name, location)
	}
}

import scala.collection.{Map, mutable}
/** A Project that determines its tasks by reflectively finding all vals with a type
* that conforms to Task.*/
trait ReflectiveTasks extends Project
{
	def tasks: Map[String, Task] = reflectiveTaskMappings
	def reflectiveTaskMappings : Map[String, Task] = Reflective.reflectiveMappings[Task](this)
}
/** A Project that determines its method tasks by reflectively finding all vals with a type
* that conforms to MethodTask.*/
trait ReflectiveMethods extends Project
{
	def methods: Map[String, MethodTask] = reflectiveMethodMappings
	def reflectiveMethodMappings : Map[String, MethodTask] = Reflective.reflectiveMappings[MethodTask](this)
}
/** A Project that determines its dependencies on other projects by reflectively
* finding all vals with a type that conforms to Project.*/
trait ReflectiveModules extends Project
{
	override def subProjects: Map[String, Project] = reflectiveModuleMappings
	def reflectiveModuleMappings : Map[String, Project] = Reflective.reflectiveMappings[Project](this)
}
/** A Project that determines its dependencies on other projects by reflectively
* finding all vals with a type that conforms to Project and determines its tasks
* by reflectively finding all vals with a type that conforms to Task.*/
trait ReflectiveProject extends ReflectiveModules with ReflectiveTasks with ReflectiveMethods

/** This Project subclass is used to contain other projects as dependencies.*/
class ParentProject(val info: ProjectInfo) extends BasicDependencyProject with Cleanable
{
	def dependencies: Iterable[Project] = info.dependencies ++ subProjects.values.toList
	/** The directories to which a project writes are listed here and is used
	* to check a project and its dependencies for collisions.*/
	override def outputDirectories = managedDependencyPath :: outputPath :: Nil
	def fullUnmanagedClasspath(config: Configuration) = unmanagedClasspath
}

object Reflective
{
	def reflectiveMappings[T](obj: AnyRef)(implicit m: scala.reflect.Manifest[T]): Map[String, T] =
	{
		val mappings = new mutable.HashMap[String, T]
		for ((name, value) <- ReflectUtilities.allVals[T](obj))
			mappings(ReflectUtilities.transformCamelCase(name, '-')) = value
		mappings
	}
}

/** A Project that determines its library dependencies by reflectively finding all vals with a type
* that conforms to ModuleID.*/
trait ReflectiveLibraryDependencies extends ManagedProject
{
	def excludeIDs: Iterable[ModuleID] = projectID :: Nil
	/** Defines the library dependencies of this project.  By default, this finds vals of type ModuleID defined on the project.
	* This can be overridden to directly provide dependencies */
	def libraryDependencies: Set[ModuleID] = reflectiveLibraryDependencies
	def reflectiveLibraryDependencies : Set[ModuleID] = Set[ModuleID](Reflective.reflectiveMappings[ModuleID](this).values.toList: _*) -- excludeIDs
}

trait ReflectiveConfigurations extends Project
{
	def ivyConfigurations: Iterable[Configuration] = reflectiveIvyConfigurations
	def reflectiveIvyConfigurations: Set[Configuration] = Configurations.removeDuplicates(Reflective.reflectiveMappings[Configuration](this).values.toList)
}
trait ReflectiveArtifacts extends ManagedProject
{
	def managedStyle: ManagedType
	def artifacts: Set[Artifact] =
	{
		val reflective = reflectiveArtifacts
		managedStyle match
		{
			case Maven => reflective ++ List(Artifact(artifactID, "pom", "pom"))
			case Ivy => reflective
			case Auto => reflective
		}
	}
	def reflectiveArtifacts: Set[Artifact] = Set(Reflective.reflectiveMappings[Artifact](this).values.toList: _*)
}
/** A Project that determines its library dependencies by reflectively finding all vals with a type
* that conforms to ModuleID.*/
trait ReflectiveRepositories extends Project
{
	def repositories: Set[Resolver] =
	{
		val reflective = Set[Resolver]() ++ reflectiveRepositories.toList.flatMap {  case (PublishToName, _) => Nil; case (_, value) => List(value) }
		info.parent match
		{
			case Some(p: ReflectiveRepositories) => p.repositories ++ reflective
			case None => reflective
		}
	}
	def reflectiveRepositories: Map[String, Resolver] = Reflective.reflectiveMappings[Resolver](this)

	def moduleConfigurations: Set[ModuleConfiguration] =
	{
		val reflective = Set[ModuleConfiguration](reflectiveModuleConfigurations.values.toList: _*)
		info.parent match
		{
			case Some(p: ReflectiveRepositories) => p.moduleConfigurations ++ reflective
			case None => reflective
		}
	}
	def reflectiveModuleConfigurations: Map[String, ModuleConfiguration] = Reflective.reflectiveMappings[ModuleConfiguration](this)
}

trait ReflectiveManagedProject extends ReflectiveProject with ReflectiveArtifacts with ReflectiveRepositories with ReflectiveLibraryDependencies with ReflectiveConfigurations