/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package sbt

import java.io.File
import java.net.{URI, URL}
import scala.xml.NodeSeq
import org.apache.ivy.plugins.resolver.IBiblioResolver
import org.apache.ivy.util.url.CredentialsStore

sealed abstract class Manager extends NotNull
/** This explicitly requests auto detection as a dependency manager.  It will first check for a 'pom.xml' file and if that does not exist, an 'ivy.xml' file.
* Ivy is configured using the detected file or uses defaults.*/
final class AutoDetectManager(val module: ModuleID, val defaultArtifact: Boolean) extends Manager
{
	def this(module: ModuleID) = this(module, true)
}
/** This explicitly requests that the Maven pom 'pom' be used to determine dependencies.  An Ivy configuration file to use may be specified in
* 'configuration', since Ivy currently cannot extract Maven repositories from a pom file. Otherwise, defaults are used.*/
final class MavenManager(val configuration: Option[Path], val pom: Path) extends Manager
/** This explicitly requests that the Ivy file 'dependencies' be used to determine dependencies.  An Ivy configuration file to use may be specified in
* 'configuration'.  Otherwise, defaults are used.*/
final class IvyManager(val configuration: Option[Path], val dependencies: Path) extends Manager
/** This manager directly specifies the dependencies, resolvers, and configurations through sbt wrapper classes and through an in-memory
* Ivy XML file. */
sealed trait SbtManager extends Manager
{
	def autodetect: Boolean
	def module: ModuleID
	def resolvers: Seq[Resolver]
	def moduleConfigurations: Seq[ModuleConfiguration]
	def dependencies: Iterable[ModuleID]
	def autodetectUnspecified: Boolean
	def dependenciesXML: NodeSeq
	def configurations: Iterable[Configuration]
	def defaultConfiguration: Option[Configuration]
}
final class SimpleManager private[sbt] (val dependenciesXML: NodeSeq, val autodetectUnspecified: Boolean,
	val module: ModuleID, val resolvers: Seq[Resolver], val moduleConfigurations: Seq[ModuleConfiguration],
	explicitConfigurations: Iterable[Configuration],	val defaultConfiguration: Option[Configuration],
	val dependencies: ModuleID*) extends SbtManager
{
	def autodetect = dependencies.isEmpty && dependenciesXML.isEmpty && module.explicitArtifacts.isEmpty && explicitConfigurations.isEmpty && autodetectUnspecified
	def configurations =
		if(explicitConfigurations.isEmpty && !autodetect)
		{
			defaultConfiguration match
			{
				case Some(Configurations.DefaultIvyConfiguration) => Configurations.Default :: Nil
				case Some(Configurations.DefaultMavenConfiguration) => Configurations.defaultMavenConfigurations
				case _ => Nil
			}
		}
		else
			explicitConfigurations
}
final case class ModuleConfiguration(organization: String, name: String, revision: String, resolver: Resolver) extends NotNull
object ModuleConfiguration
{
	def apply(org: String, resolver: Resolver): ModuleConfiguration = apply(org, "*", "*", resolver)
	def apply(org: String, name: String, resolver: Resolver): ModuleConfiguration = ModuleConfiguration(org, name, "*", resolver)
}
final case class ModuleID(organization: String, name: String, revision: String, configurations: Option[String], isChanging: Boolean, isTransitive: Boolean, explicitArtifacts: Seq[Artifact], extraAttributes: Map[String,String]) extends NotNull
{
	override def toString = organization + ":" + name + ":" + revision
	// () required for chaining
	def notTransitive() = intransitive()
	def intransitive() = ModuleID(organization, name, revision, configurations, isChanging, false, explicitArtifacts, extraAttributes)
	def changing() = ModuleID(organization, name, revision, configurations, true, isTransitive, explicitArtifacts, extraAttributes)
	def from(url: String) = artifacts(Artifact(name, new URL(url)))
	def classifier(c: String) = artifacts(Artifact(name, c))
	def artifacts(newArtifacts: Artifact*) = ModuleID(organization, name, revision, configurations, isChanging, isTransitive, newArtifacts ++ explicitArtifacts, extraAttributes)
	def extra(attributes: (String,String)*) = ModuleID(organization, name, revision, configurations, isChanging, isTransitive, explicitArtifacts, extraAttributes ++ ModuleID.checkE(attributes))
}
object ModuleID
{
	def apply(organization: String, name: String, revision: String): ModuleID = ModuleID(organization, name, revision, None)
	def apply(organization: String, name: String, revision: String, configurations: Option[String]): ModuleID =
		ModuleID(organization, name, revision, configurations, false, true)
	def apply(organization: String, name: String, revision: String, configurations: Option[String], isChanging: Boolean, isTransitive: Boolean): ModuleID =
		ModuleID(organization, name, revision, configurations, isChanging, isTransitive, Nil)
	def apply(organization: String, name: String, revision: String, configurations: Option[String], isChanging: Boolean, isTransitive: Boolean, explicitArtifacts: Seq[Artifact]): ModuleID =
		ModuleID(organization, name, revision, configurations, isChanging, isTransitive, explicitArtifacts, Map.empty)

	def checkE(attributes: Seq[(String, String)]) =
		for ( (key, value) <- attributes) yield
			if(key.startsWith("e:")) (key, value) else ("e:" + key, value)
}
sealed trait Resolver extends NotNull
{
	def name: String
}
sealed case class MavenRepository(name: String, root: String) extends Resolver
{
	override def toString = name + ": " + root
}

final class Patterns(val ivyPatterns: Seq[String], val artifactPatterns: Seq[String], val isMavenCompatible: Boolean) extends NotNull
{
	private[sbt] def mavenStyle(): Patterns = Patterns(ivyPatterns, artifactPatterns, true)
	private[sbt] def withIvys(patterns: Seq[String]): Patterns = Patterns(patterns ++ ivyPatterns, artifactPatterns, isMavenCompatible)
	private[sbt] def withArtifacts(patterns: Seq[String]): Patterns = Patterns(ivyPatterns, patterns ++ artifactPatterns, isMavenCompatible)
}
object Patterns
{
	def apply(artifactPatterns: String*): Patterns = Patterns(true, artifactPatterns : _*)
	def apply(isMavenCompatible: Boolean, artifactPatterns: String*): Patterns = Patterns(Nil, artifactPatterns, isMavenCompatible)
	def apply(ivyPatterns: Seq[String], artifactPatterns: Seq[String], isMavenCompatible: Boolean): Patterns = new Patterns(ivyPatterns, artifactPatterns, isMavenCompatible)
}
object RepositoryHelpers
{
	final case class SshConnection(authentication: Option[SshAuthentication], hostname: Option[String], port: Option[Int]) extends NotNull
	{
		def copy(authentication: Option[SshAuthentication]) = SshConnection(authentication, hostname, port)
	}
	/** Configuration specific to an Ivy filesystem resolver. */
	final case class FileConfiguration(isLocal: Boolean, isTransactional: Option[Boolean]) extends NotNull
	{
		def transactional() = FileConfiguration(isLocal, Some(true))
		def nontransactional() = FileConfiguration(isLocal, Some(false))
		def nonlocal() = FileConfiguration(false, isTransactional)
	}
	sealed trait SshAuthentication extends NotNull
	final case class PasswordAuthentication(user: String, password: String) extends SshAuthentication
	final case class KeyFileAuthentication(keyfile: File, password: String) extends SshAuthentication
}
import RepositoryHelpers.{SshConnection, FileConfiguration}
import RepositoryHelpers.{KeyFileAuthentication, PasswordAuthentication, SshAuthentication}

/** sbt interface to an Ivy repository based on patterns, which is most Ivy repositories.*/
sealed abstract class PatternsBasedRepository extends Resolver
{
	type RepositoryType <: PatternsBasedRepository
	/** Should be implemented to create a new copy of this repository but with `patterns` as given.*/
	protected def copy(patterns: Patterns): RepositoryType

	/** The object representing the configured patterns for this repository. */
	def patterns: Patterns

	/** Enables maven 2 compatibility for this repository. */
	def mavenStyle() = copy(patterns.mavenStyle())
	/** Adds the given patterns for resolving/publishing Ivy files.*/
	def ivys(ivyPatterns: String*): RepositoryType = copy(patterns.withIvys(ivyPatterns))
	/** Adds the given patterns for resolving/publishing artifacts.*/
	def artifacts(artifactPatterns: String*): RepositoryType = copy(patterns.withArtifacts(artifactPatterns))
}
/** sbt interface for an Ivy filesystem repository.  More convenient construction is done using Resolver.file.  */
final case class FileRepository(name: String, configuration: FileConfiguration, patterns: Patterns) extends PatternsBasedRepository
{
	type RepositoryType = FileRepository
	protected def copy(patterns: Patterns): FileRepository = FileRepository(name, configuration, patterns)
	private def copy(configuration: FileConfiguration) = FileRepository(name, configuration, patterns)
	def transactional() = copy(configuration.transactional())
	def nonlocal() = copy(configuration.nonlocal())
}
final case class URLRepository(name: String, patterns: Patterns) extends PatternsBasedRepository
{
	type RepositoryType = URLRepository
	protected def copy(patterns: Patterns): URLRepository = URLRepository(name, patterns)
}
/** sbt interface for an Ivy ssh-based repository (ssh and sftp).  Requires the Jsch library.. */
sealed abstract class SshBasedRepository extends PatternsBasedRepository
{
	type RepositoryType <: SshBasedRepository
	protected def copy(connection: SshConnection): RepositoryType
	private def copy(authentication: SshAuthentication): RepositoryType = copy(connection.copy(Some(authentication)))

	/** The object representing the configured ssh connection for this repository. */
	def connection: SshConnection

	/** Configures this to use the specified user name and password when connecting to the remote repository. */
	def as(user: String, password: String): RepositoryType = copy(new PasswordAuthentication(user, password))
	/** Configures this to use the specified keyfile and password for the keyfile when connecting to the remote repository. */
	def as(keyfile: File, password: String): RepositoryType = copy(new KeyFileAuthentication(keyfile, password))
}
/** sbt interface for an Ivy repository over ssh.  More convenient construction is done using Resolver.ssh.  */
final case class SshRepository(name: String, connection: SshConnection, patterns: Patterns, publishPermissions: Option[String]) extends SshBasedRepository
{
	type RepositoryType = SshRepository
	protected def copy(patterns: Patterns): SshRepository = SshRepository(name, connection, patterns, publishPermissions)
	protected def copy(connection: SshConnection): SshRepository = SshRepository(name, connection, patterns, publishPermissions)
	/** Defines the permissions to set when publishing to this repository. */
	def withPermissions(publishPermissions: String): SshRepository = withPermissions(Some(publishPermissions))
	def withPermissions(publishPermissions: Option[String]): SshRepository = SshRepository(name, connection, patterns, publishPermissions)
}
/** sbt interface for an Ivy repository over sftp.  More convenient construction is done using Resolver.sftp.  */
final case class SftpRepository(name: String, connection: SshConnection, patterns: Patterns) extends SshBasedRepository
{
	type RepositoryType = SftpRepository
	protected def copy(patterns: Patterns): SftpRepository = SftpRepository(name, connection, patterns)
	protected def copy(connection: SshConnection): SftpRepository = SftpRepository(name, connection, patterns)
}

import Resolver._
object ScalaToolsReleases extends MavenRepository(ScalaToolsReleasesName, ScalaToolsReleasesRoot)
object ScalaToolsSnapshots extends MavenRepository(ScalaToolsSnapshotsName, ScalaToolsSnapshotsRoot)
object DefaultMavenRepository extends MavenRepository("public", IBiblioResolver.DEFAULT_M2_ROOT)
object JavaNet1Repository extends Resolver
{
	def name = "java.net Maven1 Repository"
}

object Resolver
{
	val ScalaToolsReleasesName = "Scala-Tools Maven2 Repository"
	val ScalaToolsSnapshotsName = "Scala-Tools Maven2 Snapshots Repository"
	val ScalaToolsReleasesRoot = "http://scala-tools.org/repo-releases"
	val ScalaToolsSnapshotsRoot = "http://scala-tools.org/repo-snapshots"

	/** A base class for defining factories for interfaces to Ivy repositories that require a hostname , port, and patterns.  */
	sealed abstract class Define[RepositoryType <: SshBasedRepository] extends NotNull
	{
		/** Subclasses should implement this method to */
		protected def construct(name: String, connection: SshConnection, patterns: Patterns): RepositoryType
		/** Constructs this repository type with the given `name`.  `basePatterns` are the initial patterns to use.  A ManagedProject
		* has an implicit defining these initial patterns based on a setting for either Maven or Ivy style patterns.*/
		def apply(name: String)(implicit basePatterns: Patterns): RepositoryType =
			apply(name, None, None, None)
		/** Constructs this repository type with the given `name` and `hostname`.  `basePatterns` are the initial patterns to use.
		* A ManagedProject has an implicit defining these initial patterns based on a setting for either Maven or Ivy style patterns.*/
		def apply(name: String, hostname: String)(implicit basePatterns: Patterns): RepositoryType =
			apply(name, Some(hostname), None, None)
		/** Constructs this repository type with the given `name`, `hostname`, and the `basePath` against which the initial
		* patterns will be resolved.  `basePatterns` are the initial patterns to use.
		* A ManagedProject has an implicit defining these initial patterns based on a setting for either Maven or Ivy style patterns.*/
		def apply(name: String, hostname: String, basePath: String)(implicit basePatterns: Patterns): RepositoryType =
			apply(name, Some(hostname), None, Some(basePath))
		/** Constructs this repository type with the given `name`, `hostname`, and `port`.  `basePatterns` are the initial patterns to use.
		* A ManagedProject has an implicit defining these initial patterns based on a setting for either Maven or Ivy style patterns.*/
		def apply(name: String, hostname: String, port: Int)(implicit basePatterns: Patterns): RepositoryType =
			apply(name, Some(hostname), Some(port), None)
		/** Constructs this repository type with the given `name`, `hostname`, `port`, and the `basePath` against which the initial
		* patterns will be resolved.  `basePatterns` are the initial patterns to use.
		* A ManagedProject has an implicit defining these initial patterns based on a setting for either Maven or Ivy style patterns.*/
		def apply(name: String, hostname: String, port: Int, basePath: String)(implicit basePatterns: Patterns): RepositoryType =
			apply(name, Some(hostname), Some(port), Some(basePath))
		/** Constructs this repository type with the given `name`, `hostname`, `port`, and the `basePath` against which the initial
		* patterns will be resolved.  `basePatterns` are the initial patterns to use.  All but the `name` are optional (use None).
		* A ManagedProject has an implicit defining these initial patterns based on a setting for either Maven or Ivy style patterns.*/
		def apply(name: String, hostname: Option[String], port: Option[Int], basePath: Option[String])(implicit basePatterns: Patterns): RepositoryType =
			construct(name, SshConnection(None, hostname, port), resolvePatterns(basePath, basePatterns))
	}
	/** A factory to construct an interface to an Ivy SSH resolver.*/
	object ssh extends Define[SshRepository]
	{
		protected def construct(name: String, connection: SshConnection, patterns: Patterns) = SshRepository(name, connection, patterns, None)
	}
	/** A factory to construct an interface to an Ivy SFTP resolver.*/
	object sftp extends Define[SftpRepository]
	{
		protected def construct(name: String, connection: SshConnection, patterns: Patterns) = SftpRepository(name, connection, patterns)
	}
	/** A factory to construct an interface to an Ivy filesytem resolver. */
	object file
	{
		/** Constructs a file resolver with the given name.  The patterns to use must be explicitly specified
		* using the `ivys` or `artifacts` methods on the constructed resolver object.*/
		def apply(name: String): FileRepository = FileRepository(name, defaultFileConfiguration, ivyStylePatterns)
		/** Constructs a file resolver with the given name and base directory. */
		def apply(name: String, baseDirectory: File)(implicit basePatterns: Patterns): FileRepository =
		{
			if(baseDirectory.exists && !baseDirectory.isDirectory) error("Not a directory: " + baseDirectory.getAbsolutePath) else baseDirectory.mkdirs()
			baseRepository(baseDirectory.toURI)(FileRepository(name, defaultFileConfiguration, _))
		}
	}
	object url
	{
		/** Constructs a URL resolver with the given name.  The patterns to use must be explicitly specified
		* using the `ivys` or `artifacts` methods on the constructed resolver object.*/
		def apply(name: String): URLRepository = URLRepository(name, ivyStylePatterns)
		/** Constructs a file resolver with the given name and base directory. */
		def apply(name: String, baseURL: URL)(implicit basePatterns: Patterns): URLRepository =
			baseRepository(baseURL.toURI)(URLRepository(name, _))
	}
	private def baseRepository[T](baseURI: java.net.URI)(construct: Patterns => T)(implicit basePatterns: Patterns): T =
		construct(resolvePatterns(baseURI.normalize, basePatterns))

	/** If `base` is None, `patterns` is returned unchanged.
	* Otherwise, the ivy file and artifact patterns in `patterns` are resolved against the given base. */
	private def resolvePatterns(base: Option[String], patterns: Patterns): Patterns =
		base match
		{
			case Some(path) => resolvePatterns(pathURI(path), patterns)
			case None => patterns
		}
	/** Resolves the ivy file and artifact patterns in `patterns` against the given base. */
	private def resolvePatterns(base: URI, basePatterns: Patterns): Patterns =
	{
		def resolve(pattern: String) = base.resolve(pathURI(pattern)).getPath
		def resolveAll(patterns: Seq[String]) = patterns.map(resolve)
		Patterns(resolveAll(basePatterns.ivyPatterns), resolveAll(basePatterns.artifactPatterns), basePatterns.isMavenCompatible)
	}
	/** Constructs a `URI` with the path component set to `path` and the other components set to null.*/
	private def pathURI(path: String) = new URI(null, null, path, null)

	def defaultFileConfiguration = FileConfiguration(true, None)
	def mavenStylePatterns = Patterns(Nil, mavenStyleBasePattern :: Nil, true)
	def ivyStylePatterns = Patterns(Nil, Nil, false)

	def defaultPatterns = mavenStylePatterns
	def mavenStyleBasePattern = "[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]"
	def localBasePattern = "[organisation]/[module]/[revision]/[type]s/[artifact].[ext]"

	def userRoot = System.getProperty("user.home")
	def userMavenRoot = userRoot + "/.m2/repository/"
	def userIvyRoot = userRoot + "/.ivy2/"

	def defaultLocal = defaultUserFileRepository("local")
	def defaultShared = defaultUserFileRepository("shared")
	def defaultUserFileRepository(id: String) = file(id, new File(userIvyRoot, id))(defaultIvyPatterns)
	def defaultIvyPatterns =
	{
		val pList = List(localBasePattern)
		Patterns(pList, pList, false)
	}
}

object Configurations
{
	def config(name: String) = new Configuration(name)
	def defaultMavenConfigurations = Compile :: Runtime :: Test :: Provided :: System :: Optional :: Sources :: Javadoc :: Nil

	lazy val Default = config("default")
	lazy val Compile = config("compile")
	lazy val IntegrationTest = config("it") hide
	lazy val Provided = config("provided")
	lazy val Javadoc = config("javadoc")
	lazy val Runtime = config("runtime")
	lazy val Test = config("test") hide
	lazy val Sources = config("sources")
	lazy val System = config("system")
	lazy val Optional = config("optional")

	lazy val CompilerPlugin = config("plugin") hide

	def allPredefined = Default :: IntegrationTest :: CompilerPlugin :: defaultMavenConfigurations

	private[sbt] val DefaultMavenConfiguration = defaultConfiguration(true)
	private[sbt] val DefaultIvyConfiguration = defaultConfiguration(false)
	private[sbt] def DefaultConfiguration(mavenStyle: Boolean) = if(mavenStyle) DefaultMavenConfiguration else DefaultIvyConfiguration
	private[sbt] def defaultConfiguration(mavenStyle: Boolean) = if(mavenStyle) Configurations.Compile else Configurations.Default
	private[sbt] def removeDuplicates(configs: Iterable[Configuration]) = Set(scala.collection.mutable.Map(configs.map(config => (config.name, config)).toSeq: _*).values.toList: _*)
}
/** Represents an Ivy configuration. */
final case class Configuration(name: String, description: String, isPublic: Boolean, extendsConfigs: List[Configuration], transitive: Boolean) extends NotNull
{
	require(name != null && !name.isEmpty)
	require(description != null)
	def this(name: String) = this(name, "", true, Nil, true)
	def describedAs(newDescription: String) = Configuration(name, newDescription, isPublic, extendsConfigs, transitive)
	def extend(configs: Configuration*) = Configuration(name, description, isPublic, configs.toList ::: extendsConfigs, transitive)
	def notTransitive = intransitive
	def intransitive = Configuration(name, description, isPublic, extendsConfigs, false)
	def hide = Configuration(name, description, false, extendsConfigs, transitive)
	override def toString = name
}

final case class Artifact(name: String, `type`: String, extension: String, classifier: Option[String], configurations: Iterable[Configuration], url: Option[URL], extraAttributes: Map[String,String]) extends NotNull
{
	def extra(attributes: (String,String)*) = Artifact(name, `type`, extension, classifier, configurations, url, extraAttributes ++ attributes)
}
object Artifact
{
	def apply(name: String): Artifact = Artifact(name, defaultType, defaultExtension, None, Nil, None)
	def apply(name: String, extra: Map[String,String]): Artifact = Artifact(name, defaultType, defaultExtension, None, Nil, None, extra)
	def apply(name: String, classifier: String): Artifact = Artifact(name, defaultType, defaultExtension, Some(classifier), Nil, None)
	def apply(name: String, `type`: String, extension: String): Artifact = Artifact(name, `type`, extension, None, Nil, None)
	def apply(name: String, url: URL): Artifact =Artifact(name, extract(url, defaultType), extract(url, defaultExtension), None, Nil, Some(url))
	def apply(name: String, `type`: String, extension: String, classifier: Option[String], configurations: Iterable[Configuration], url: Option[URL]): Artifact =
		Artifact(name, `type`, extension, classifier, configurations, url, Map.empty)
	val defaultExtension = "jar"
	val defaultType = "jar"
	private[this] def extract(url: URL, default: String) =
	{
		val s = url.toString
		val i = s.lastIndexOf('.')
		if(i >= 0)
			s.substring(i+1)
		else
			default
	}
}

object Credentials
{
	/** Add the provided credentials to Ivy's credentials cache.*/
	def add(realm: String, host: String, userName: String, passwd: String): Unit =
		CredentialsStore.INSTANCE.addCredentials(realm, host, userName, passwd)
	/** Load credentials from the given file into Ivy's credentials cache.*/
	def apply(file: String, log: Logger): Unit = apply(Path.fromFile(file), log)
	/** Load credentials from the given file into Ivy's credentials cache.*/
	def apply(file: File, log: Logger): Unit = apply(Path.fromFile(file), log)
	/** Load credentials from the given file into Ivy's credentials cache.*/
	def apply(path: Path, log: Logger)
	{
		val msg =
			if(path.exists)
			{
				val properties = new scala.collection.mutable.HashMap[String, String]
				def get(keys: List[String]) = keys.flatMap(properties.get).firstOption.toRight(keys.head + " not specified in credentials file: " + path)

					impl.MapUtilities.read(properties, path, log) orElse
					{
						List.separate( List(RealmKeys, HostKeys, UserKeys, PasswordKeys).map(get) ) match
						{
							case (Nil, List(realm, host, user, pass)) => add(realm, host, user, pass); None
							case (errors, _) => Some(errors.mkString("\n"))
						}
					}
			}
			else
				Some("Credentials file " + path + " does not exist")
		msg.foreach(x => log.warn(x))
	}
	private[this] val RealmKeys = List("realm")
	private[this] val HostKeys = List("host", "hostname")
	private[this] val UserKeys = List("user", "user.name", "username")
	private[this] val PasswordKeys = List("password", "pwd", "pass", "passwd")
}