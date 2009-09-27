/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package sbt

import StringUtilities.{appendable,nonEmpty}

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
		val base = descendents(dependencyPath, "*.jar")
		if(scratch)
			base +++ (info.projectPath * "*.jar")
		else
			base
	}
	/** The classpath containing all unmanaged classpath elements for the given configuration. This typically includes
	* at least 'unmanagedClasspath'.*/
	def fullUnmanagedClasspath(config: Configuration): PathFinder
}

/** A project that provides automatic dependency management.*/
trait ManagedProject extends ClasspathProject
{
	trait ManagedOption extends ActionOption
	final class ManagedFlagOption extends ManagedOption
	/** An update option that specifies that unneeded files should be pruned from the managed library directory
	* after updating. */
	final val Synchronize = new ManagedFlagOption
	/** An update option that specifies that Ivy should validate configurations.*/
	final val Validate = new ManagedFlagOption
	/** An update option that puts Ivy into a quieter logging mode.*/
	final val QuietUpdate = new ManagedFlagOption
	/** An update option that adds the scala-tools.org releases repository to the set of resolvers, unless
	* no inline repositories are present and an ivysettings.xml file is present.*/
	final val AddScalaToolsReleases = new ManagedFlagOption
	/** An update option that specifies that an error should be generated if no inline dependencies, resolvers,
	* XML file, or Ivy or Maven configuration files are present.*/
	final val ErrorIfNoConfiguration = new ManagedFlagOption
	/** An update option that explicitly specifies the dependency manager to use.  This can be used to
	* override the default precendence. */
	final case class LibraryManager(m: Manager) extends ManagedOption
	/** An update option that overrides the default Ivy cache location. */
	final case class CacheDirectory(dir: Path) extends ManagedOption
	final case class CheckScalaVersion(configs: Iterable[Configuration], checkExplicit: Boolean, filterImplicit: Boolean) extends ManagedOption

	protected def withConfigurations(outputPattern: String, managedDependencyPath: Path, options: Seq[ManagedOption])
		(doWith: (IvyConfiguration, UpdateConfiguration) => Option[String]) =
	{
		var synchronize = false
		var validate = false
		var quiet = false
		var addScalaTools = false
		var errorIfNoConfiguration = false
		var manager: Manager = new AutoDetectManager(projectID)
		var cacheDirectory: Option[Path] = None
		var checkScalaVersion: Option[IvyScala] = None
		for(option <- options)
		{
			option match
			{
				case Synchronize => synchronize = true
				case Validate => validate = true
				case LibraryManager(m) => manager = m
				case QuietUpdate => quiet = true
				case AddScalaToolsReleases => addScalaTools = true
				case ErrorIfNoConfiguration => errorIfNoConfiguration = true
				case CacheDirectory(dir) => cacheDirectory = Some(dir)
				case CheckScalaVersion(configs, checkExplicit, filterImplicit) =>
					checkScalaVersion = getScalaVersion.map(version => new IvyScala(version, configs, checkExplicit, filterImplicit))
				case _ => log.warn("Ignored unknown managed option " + option)
			}
		}
		val ivyPaths = new IvyPaths(info.projectPath, managedDependencyPath, cacheDirectory)
		val ivyFlags = new IvyFlags(validate, addScalaTools, errorIfNoConfiguration)
		val ivyConfiguration = new IvyConfiguration(ivyPaths, manager, ivyFlags, checkScalaVersion, log)
		val updateConfiguration = new UpdateConfiguration(outputPattern, synchronize, quiet)
		doWith(ivyConfiguration, updateConfiguration)
	}
	private def getScalaVersion =
	{
		val v = scalaVersion.value
		if(v.isEmpty) None
		else Some(v)
	}
	protected def withIvyTask(doTask: => Option[String]) =
		task
		{
			try { doTask }
			catch
			{
				case e: NoClassDefFoundError =>
					log.trace(e)
					Some("Apache Ivy is required for dependency management (" + e.toString + ")")
			}
		}
	def updateTask(outputPattern: String, managedDependencyPath: Path, options: ManagedOption*): Task =
		updateTask(outputPattern, managedDependencyPath, options)
	def updateTask(outputPattern: String, managedDependencyPath: Path, options: => Seq[ManagedOption]) =
		withIvyTask(withConfigurations(outputPattern, managedDependencyPath, options)(ManageDependencies.update))

	def publishTask(publishConfiguration: => PublishConfiguration, options: => Seq[ManagedOption]) =
		withIvyTask
		{
			val publishConfig = publishConfiguration
			import publishConfig._
			withConfigurations("", managedDependencyPath, options) { (ivyConf, ignore) =>
				val delivered = if(publishIvy) Some(deliveredPattern) else None
				ManageDependencies.publish(ivyConf, resolverName, srcArtifactPatterns, delivered, configurations) }
		}
	def deliverTask(deliverConfiguration: => PublishConfiguration, options: => Seq[ManagedOption]) =
		withIvyTask
		{
			val deliverConfig = deliverConfiguration
			import deliverConfig._
			withConfigurations("", managedDependencyPath, options) { (ivyConf, updateConf) =>
				ManageDependencies.deliver(ivyConf, updateConf, status, deliveredPattern, extraDependencies, configurations)
			}
		}
	def makePomTask(output: => Path, extraDependencies: => Iterable[ModuleID], configurations: => Option[Iterable[Configuration]], options: => Seq[ManagedOption]) =
		withIvyTask(withConfigurations("", managedDependencyPath, options) { (ivyConf, ignore) =>
			ManageDependencies.makePom(ivyConf, extraDependencies, configurations, output.asFile) })

	def cleanCacheTask(managedDependencyPath: Path, options: => Seq[ManagedOption]) =
		withIvyTask(withConfigurations("", managedDependencyPath, options) { (ivyConf, ignore) => ManageDependencies.cleanCache(ivyConf) })

	def cleanLibTask(managedDependencyPath: Path) = task { FileUtilities.clean(managedDependencyPath.get, log) }

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
	final def configurationClasspath(config: Configuration): PathFinder = descendents(configurationPath(config), "*.jar")
	/** The base path to which dependencies in configuration 'config' are downloaded.*/
	def configurationPath(config: Configuration): Path = managedDependencyPath / config.toString

	import StringUtilities.nonEmpty
	implicit def toGroupID(groupID: String): GroupID =
	{
		nonEmpty(groupID, "Group ID")
		new GroupID(groupID, ScalaVersion.currentString)
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
	import BasicManagedProject._
	/** The dependency manager that represents inline declarations.  The default manager packages the information
	* from 'ivyXML', 'projectID', 'repositories', and 'libraryDependencies' and does not typically need to be
	* be overridden. */
	def manager = new SimpleManager(ivyXML, true, projectID, repositories.toSeq, moduleConfigurations.toSeq, ivyConfigurations, defaultConfiguration, libraryDependencies.toList: _*)

	/** The pattern for Ivy to use when retrieving dependencies into the local project.  Classpath management
	* depends on the first directory being [conf] and the extension being [ext].*/
	def outputPattern = "[conf]/[artifact](-[revision]).[ext]"
	/** Override this to specify the publications, configurations, and/or dependencies sections of an Ivy file.
	* See http://code.google.com/p/simple-build-tool/wiki/LibraryManagement for details.*/
	def ivyXML: scala.xml.NodeSeq = scala.xml.NodeSeq.Empty
	/** The base options passed to the 'update' action. */
	def baseUpdateOptions = checkScalaVersion :: Validate :: Synchronize :: QuietUpdate :: AddScalaToolsReleases :: Nil
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
	/** The options provided to the 'update' action.  This is by default the options in 'baseUpdateOptions'.
	* If 'manager' has any dependencies, resolvers, or inline Ivy XML (which by default happens when inline
	* dependency management is used), it is passed as the dependency manager.*/
	def updateOptions: Seq[ManagedOption] = baseUpdateOptions ++ managerOption
	def managerOption: Seq[ManagedOption] =
	{
		val m = manager
		if(m.dependencies.isEmpty && m.resolvers.isEmpty && ivyXML.isEmpty && m.module.explicitArtifacts.isEmpty && m.configurations.isEmpty)
			Nil
		else
			LibraryManager(m) :: Nil
	}
	def deliverOptions: Seq[ManagedOption] = updateOptions.filter { case _: CheckScalaVersion => false; case _ => true }
	def publishOptions: Seq[ManagedOption] = deliverOptions
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
	def checkScalaInConfigurations: Iterable[Configuration] =
	{
		val all = ivyConfigurations
		if(all.isEmpty)
			Configurations.defaultMavenConfigurations
		else
			all
	}
	def checkScalaVersion = CheckScalaVersion(checkScalaInConfigurations, checkExplicitScalaDependencies, filterScalaJars)
	def defaultPublishRepository: Option[Resolver] =
	{
		reflectiveRepositories.get("publish-to") orElse
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

	protected def updateAction = updateTask(outputPattern, managedDependencyPath, updateOptions) describedAs UpdateDescription
	protected def cleanLibAction = cleanLibTask(managedDependencyPath) describedAs CleanLibDescription
	protected def cleanCacheAction = cleanCacheTask(managedDependencyPath, updateOptions) describedAs CleanCacheDescription

	protected def deliverProjectDependencies: Iterable[ModuleID] =
	{
		val interDependencies = new scala.collection.mutable.ListBuffer[ModuleID]
		dependencies.foreach(dep => dep match { case mp: ManagedProject => interDependencies += mp.projectID; case _ => () })
		if(filterScalaJars)
			interDependencies ++= deliverScalaDependencies
		interDependencies.readOnly
	}
	protected def deliverScalaDependencies: Iterable[ModuleID] = Nil
	protected def makePomAction = makePomTask(pomPath, deliverProjectDependencies, None, updateOptions)
	protected def deliverLocalAction = deliverTask(publishLocalConfiguration, deliverOptions)
	protected def publishLocalAction =
	{
		val dependencies = deliverLocal :: publishPomDepends
		publishTask(publishLocalConfiguration, publishOptions) dependsOn(dependencies : _*)
	}
	protected def publishLocalConfiguration = new DefaultPublishConfiguration("local", "release", true)
	protected def deliverAction = deliverTask(publishConfiguration, deliverOptions)
	protected def publishAction =
	{
		val dependencies = deliver :: publishPomDepends
		publishTask(publishConfiguration, publishOptions) dependsOn(dependencies : _*)
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

		protected def deliveredPathPattern = outputPath / "[artifact]-[revision].[ext]"
		def deliveredPattern = deliveredPathPattern.relativePath
		def srcArtifactPatterns: Iterable[String] =
		{
			val pathPatterns =
				(outputPath / "[artifact]-[revision]-[type].[ext]") ::
				(outputPath / "[artifact]-[revision].[ext]") ::
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
}

class DefaultInstallProject(val info: ProjectInfo) extends InstallProject with MavenStyleScalaPaths with BasicDependencyProject
{
	def fullUnmanagedClasspath(config: Configuration) = unmanagedClasspath
	def dependencies = info.dependencies
}
trait InstallProject extends BasicManagedProject
{
	def installOptions: Seq[ManagedOption] = updateOptions
	override def filterScalaJars = false
	override def checkExplicitScalaDependencies = false
	lazy val install = installTask(updateOptions)
	def installTask(options: => Seq[ManagedOption]) =
		withIvyTask
		{
			withConfigurations("", managedDependencyPath, options) { (ivyConf, ignore) =>
				val toResolver = reflectiveRepositories.get("publish-to").getOrElse(error("No repository to publish to was specified"))
				val fromResolver = reflectiveRepositories.get("retrieve-from").getOrElse(error("No repository to retrieve from was specified"))
				ManageDependencies.install(ivyConf, fromResolver.name, toResolver.name, true, true)
			}
		}
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
class ParentProject(val info: ProjectInfo) extends BasicDependencyProject
{
	override def managerOption: Seq[ManagedOption] = LibraryManager(new AutoDetectManager(projectID, false)) :: Nil
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
		val mappings = new mutable.OpenHashMap[String, T]
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
			case Maven =>reflective ++ List(Artifact(artifactID, "pom", "pom"))
			case Ivy => reflective
			case Auto => Set.empty
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
		val reflective = Set[Resolver](reflectiveRepositories.values.toList: _*)
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