/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt

import java.io.{ IOException, File }
import java.net.URL
import scala.xml.{ Text, NodeSeq, Elem, XML }
import org.apache.ivy.plugins.resolver.DependencyResolver
import org.xml.sax.SAXParseException

sealed trait Resolver {
  def name: String
}
final class RawRepository(val resolver: DependencyResolver) extends Resolver {
  def name = resolver.getName
  override def toString = "Raw(" + resolver.toString + ")"

  override def equals(o: Any): Boolean = o match {
    case o: RawRepository =>
      this.name == o.name
    case _ => false
  }

  override def hashCode: Int =
    {
      var hash = 1
      hash = hash * 31 + this.name.##
      hash
    }
}
sealed case class ChainedResolver(name: String, resolvers: Seq[Resolver]) extends Resolver

/** An instance of a remote maven repository.  Note:  This will use Aether/Maven to resolve artifacts. */
sealed case class MavenRepository(name: String, root: String) extends Resolver {
  override def toString = name + ": " + root
  def isCache: Boolean = false
}

/**
 * An instance of maven CACHE directory.  You cannot treat a cache directory the same as a a remote repository because
 * the metadata is different (see Aether ML discussion).
 */
final class MavenCache(name: String, val rootFile: File) extends MavenRepository(name, rootFile.toURI.toURL.toString) {
  override val toString = "cache:" + name + ": " + rootFile.getAbsolutePath
  override def isCache: Boolean = true
}
object MavenCache {
  def apply(name: String, rootFile: File): MavenCache = new MavenCache(name, rootFile)
}

final class Patterns(val ivyPatterns: Seq[String], val artifactPatterns: Seq[String], val isMavenCompatible: Boolean, val descriptorOptional: Boolean, val skipConsistencyCheck: Boolean) {
  private[sbt] def mavenStyle(): Patterns = Patterns(ivyPatterns, artifactPatterns, true)
  private[sbt] def withDescriptorOptional(): Patterns = Patterns(ivyPatterns, artifactPatterns, isMavenCompatible, true, skipConsistencyCheck)
  private[sbt] def withoutConsistencyCheck(): Patterns = Patterns(ivyPatterns, artifactPatterns, isMavenCompatible, descriptorOptional, true)
  private[sbt] def withIvys(patterns: Seq[String]): Patterns = Patterns(patterns ++ ivyPatterns, artifactPatterns, isMavenCompatible)
  private[sbt] def withArtifacts(patterns: Seq[String]): Patterns = Patterns(ivyPatterns, patterns ++ artifactPatterns, isMavenCompatible)
  override def toString = "Patterns(ivyPatterns=%s, artifactPatterns=%s, isMavenCompatible=%s, descriptorOptional=%s, skipConsistencyCheck=%s)".format(ivyPatterns, artifactPatterns, isMavenCompatible, descriptorOptional, skipConsistencyCheck)
  override def equals(obj: Any): Boolean = {
    obj match {
      case other: Patterns =>
        ivyPatterns == other.ivyPatterns && artifactPatterns == other.artifactPatterns && isMavenCompatible == other.isMavenCompatible && descriptorOptional == other.descriptorOptional && skipConsistencyCheck == other.skipConsistencyCheck
      case _ => false
    }
  }
  override def hashCode: Int = (ivyPatterns, artifactPatterns, isMavenCompatible, descriptorOptional, skipConsistencyCheck).hashCode

  @deprecated
  def this(ivyPatterns: Seq[String], artifactPatterns: Seq[String], isMavenCompatible: Boolean) = this(ivyPatterns, artifactPatterns, isMavenCompatible, false, false)
}
object Patterns {
  implicit def defaultPatterns: Patterns = Resolver.defaultPatterns

  def apply(artifactPatterns: String*): Patterns = Patterns(true, artifactPatterns: _*)
  def apply(isMavenCompatible: Boolean, artifactPatterns: String*): Patterns = Patterns(artifactPatterns, artifactPatterns, isMavenCompatible)
  def apply(ivyPatterns: Seq[String], artifactPatterns: Seq[String], isMavenCompatible: Boolean): Patterns = apply(ivyPatterns: Seq[String], artifactPatterns: Seq[String], isMavenCompatible: Boolean, false, false)
  def apply(ivyPatterns: Seq[String], artifactPatterns: Seq[String], isMavenCompatible: Boolean, descriptorOptional: Boolean, skipConsistencyCheck: Boolean): Patterns = new Patterns(ivyPatterns, artifactPatterns, isMavenCompatible, descriptorOptional, skipConsistencyCheck)
}
object RepositoryHelpers {
  final case class SshConnection(authentication: Option[SshAuthentication], hostname: Option[String], port: Option[Int]) {
    def copy(authentication: Option[SshAuthentication]) = SshConnection(authentication, hostname, port)
  }
  /** Configuration specific to an Ivy filesystem resolver. */
  final case class FileConfiguration(isLocal: Boolean, isTransactional: Option[Boolean]) {
    def transactional() = FileConfiguration(isLocal, Some(true))
    def nontransactional() = FileConfiguration(isLocal, Some(false))
    def nonlocal() = FileConfiguration(false, isTransactional)
  }
  sealed trait SshAuthentication extends NotNull
  final case class PasswordAuthentication(user: String, password: Option[String]) extends SshAuthentication
  final case class KeyFileAuthentication(user: String, keyfile: File, password: Option[String]) extends SshAuthentication
}
import RepositoryHelpers.{ SshConnection, FileConfiguration }
import RepositoryHelpers.{ KeyFileAuthentication, PasswordAuthentication, SshAuthentication }

/** sbt interface to an Ivy repository based on patterns, which is most Ivy repositories.*/
sealed abstract class PatternsBasedRepository extends Resolver {
  type RepositoryType <: PatternsBasedRepository
  /** Should be implemented to create a new copy of this repository but with `patterns` as given.*/
  protected def copy(patterns: Patterns): RepositoryType

  /** The object representing the configured patterns for this repository. */
  def patterns: Patterns

  /** Enables maven 2 compatibility for this repository. */
  def mavenStyle() = copy(patterns.mavenStyle())

  /** Makes descriptor metadata optional for this repository. */
  def descriptorOptional() = copy(patterns.withDescriptorOptional())

  /** Disables consistency checking for this repository. */
  def skipConsistencyCheck() = copy(patterns.withoutConsistencyCheck())

  /** Adds the given patterns for resolving/publishing Ivy files.*/
  def ivys(ivyPatterns: String*): RepositoryType = copy(patterns.withIvys(ivyPatterns))
  /** Adds the given patterns for resolving/publishing artifacts.*/
  def artifacts(artifactPatterns: String*): RepositoryType = copy(patterns.withArtifacts(artifactPatterns))
}
/** sbt interface for an Ivy filesystem repository.  More convenient construction is done using Resolver.file.  */
final case class FileRepository(name: String, configuration: FileConfiguration, patterns: Patterns) extends PatternsBasedRepository {
  type RepositoryType = FileRepository
  protected def copy(patterns: Patterns): FileRepository = FileRepository(name, configuration, patterns)
  private def copy(configuration: FileConfiguration) = FileRepository(name, configuration, patterns)
  def transactional() = copy(configuration.transactional())
  def nonlocal() = copy(configuration.nonlocal())
}
final case class URLRepository(name: String, patterns: Patterns) extends PatternsBasedRepository {
  type RepositoryType = URLRepository
  protected def copy(patterns: Patterns): URLRepository = URLRepository(name, patterns)
}
/** sbt interface for an Ivy ssh-based repository (ssh and sftp).  Requires the Jsch library.. */
sealed abstract class SshBasedRepository extends PatternsBasedRepository {
  type RepositoryType <: SshBasedRepository
  protected def copy(connection: SshConnection): RepositoryType
  private def copy(authentication: SshAuthentication): RepositoryType = copy(connection.copy(Some(authentication)))

  /** The object representing the configured ssh connection for this repository. */
  def connection: SshConnection

  /** Configures this to use the specified user name and password when connecting to the remote repository. */
  def as(user: String, password: String): RepositoryType = as(user, Some(password))
  def as(user: String): RepositoryType = as(user, None)
  def as(user: String, password: Option[String]) = copy(new PasswordAuthentication(user, password))
  /** Configures this to use the specified keyfile and password for the keyfile when connecting to the remote repository. */
  def as(user: String, keyfile: File): RepositoryType = as(user, keyfile, None)
  def as(user: String, keyfile: File, password: String): RepositoryType = as(user, keyfile, Some(password))
  def as(user: String, keyfile: File, password: Option[String]): RepositoryType = copy(new KeyFileAuthentication(user, keyfile, password))
}
/** sbt interface for an Ivy repository over ssh.  More convenient construction is done using Resolver.ssh.  */
final case class SshRepository(name: String, connection: SshConnection, patterns: Patterns, publishPermissions: Option[String]) extends SshBasedRepository {
  type RepositoryType = SshRepository
  protected def copy(patterns: Patterns): SshRepository = SshRepository(name, connection, patterns, publishPermissions)
  protected def copy(connection: SshConnection): SshRepository = SshRepository(name, connection, patterns, publishPermissions)
  /** Defines the permissions to set when publishing to this repository. */
  def withPermissions(publishPermissions: String): SshRepository = withPermissions(Some(publishPermissions))
  def withPermissions(publishPermissions: Option[String]): SshRepository = SshRepository(name, connection, patterns, publishPermissions)
}
/** sbt interface for an Ivy repository over sftp.  More convenient construction is done using Resolver.sftp.  */
final case class SftpRepository(name: String, connection: SshConnection, patterns: Patterns) extends SshBasedRepository {
  type RepositoryType = SftpRepository
  protected def copy(patterns: Patterns): SftpRepository = SftpRepository(name, connection, patterns)
  protected def copy(connection: SshConnection): SftpRepository = SftpRepository(name, connection, patterns)
}

import Resolver._

object DefaultMavenRepository extends MavenRepository("public", centralRepositoryRoot(useSecureResolvers))
object JavaNet2Repository extends MavenRepository(JavaNet2RepositoryName, JavaNet2RepositoryRoot)
object JCenterRepository extends MavenRepository(JCenterRepositoryName, JCenterRepositoryRoot)
@deprecated("HTTP repository is no longer recommended.", "0.13.6")
object JavaNet1Repository extends JavaNet1Repository
@deprecated("HTTP repository is no longer recommended.", "0.13.6")
sealed trait JavaNet1Repository extends Resolver {
  def name = "java.net Maven1 Repository"
}

object Resolver {
  private[sbt] def useSecureResolvers = sys.props.get("sbt.repository.secure") map { _.toLowerCase == "true" } getOrElse true

  val TypesafeRepositoryRoot = typesafeRepositoryRoot(useSecureResolvers)
  val SbtPluginRepositoryRoot = sbtPluginRepositoryRoot(useSecureResolvers)
  val SonatypeRepositoryRoot = "https://oss.sonatype.org/content/repositories"
  val JavaNet2RepositoryName = "java.net Maven2 Repository"
  val JavaNet2RepositoryRoot = javanet2RepositoryRoot(useSecureResolvers)
  val JCenterRepositoryName = "jcenter"
  val JCenterRepositoryRoot = "https://jcenter.bintray.com/"
  val DefaultMavenRepositoryRoot = "https://repo1.maven.org/maven2/"
  // TODO: This switch is only kept for backward compatibility. Hardcode to HTTPS in the future.
  private[sbt] def centralRepositoryRoot(secure: Boolean) = (if (secure) "https" else "http") + "://repo1.maven.org/maven2/"
  // TODO: This switch is only kept for backward compatibility. Hardcode to HTTPS in the future.
  private[sbt] def javanet2RepositoryRoot(secure: Boolean) =
    if (secure) "https://maven.java.net/content/repositories/public/"
    else "http://download.java.net/maven/2"
  // TODO: This switch is only kept for backward compatibility. Hardcode to HTTPS in the future.
  private[sbt] def typesafeRepositoryRoot(secure: Boolean) = (if (secure) "https" else "http") + "://repo.typesafe.com/typesafe"
  // TODO: This switch is only kept for backward compatibility. Hardcode to HTTPS in the future.
  private[sbt] def sbtPluginRepositoryRoot(secure: Boolean) = (if (secure) "https" else "http") + "://repo.scala-sbt.org/scalasbt"

  // obsolete: kept only for launcher compatibility
  private[sbt] val ScalaToolsReleasesName = "Sonatype OSS Releases"
  private[sbt] val ScalaToolsSnapshotsName = "Sonatype OSS Snapshots"
  private[sbt] val ScalaToolsReleasesRoot = SonatypeRepositoryRoot + "/releases"
  private[sbt] val ScalaToolsSnapshotsRoot = SonatypeRepositoryRoot + "/snapshots"
  private[sbt] val ScalaToolsReleases = new MavenRepository(ScalaToolsReleasesName, ScalaToolsReleasesRoot)
  private[sbt] val ScalaToolsSnapshots = new MavenRepository(ScalaToolsSnapshotsName, ScalaToolsSnapshotsRoot)

  def typesafeRepo(status: String) = new MavenRepository("typesafe-" + status, TypesafeRepositoryRoot + "/" + status)
  def typesafeIvyRepo(status: String) = url("typesafe-ivy-" + status, new URL(TypesafeRepositoryRoot + "/ivy-" + status + "/"))(ivyStylePatterns)
  def sbtPluginRepo(status: String) = url("sbt-plugin-" + status, new URL(SbtPluginRepositoryRoot + "/sbt-plugin-" + status + "/"))(ivyStylePatterns)
  def sonatypeRepo(status: String) = new MavenRepository("sonatype-" + status, SonatypeRepositoryRoot + "/" + status)
  def bintrayRepo(owner: String, repo: String) = new MavenRepository(s"bintray-$owner-$repo", s"https://dl.bintray.com/$owner/$repo/")
  def jcenterRepo = JCenterRepository

  /** Add the local and Maven Central repositories to the user repositories.  */
  def withDefaultResolvers(userResolvers: Seq[Resolver]): Seq[Resolver] =
    withDefaultResolvers(userResolvers, true)
  /**
   * Add the local Ivy repository to the user repositories.
   * If `mavenCentral` is true, add the Maven Central repository.
   */
  def withDefaultResolvers(userResolvers: Seq[Resolver], mavenCentral: Boolean): Seq[Resolver] =
    Seq(Resolver.defaultLocal) ++
      userResolvers ++
      single(DefaultMavenRepository, mavenCentral)
  private def single[T](value: T, nonEmpty: Boolean): Seq[T] = if (nonEmpty) Seq(value) else Nil

  /** A base class for defining factories for interfaces to Ivy repositories that require a hostname , port, and patterns.  */
  sealed abstract class Define[RepositoryType <: SshBasedRepository] extends NotNull {
    /** Subclasses should implement this method to */
    protected def construct(name: String, connection: SshConnection, patterns: Patterns): RepositoryType
    /**
     * Constructs this repository type with the given `name`.  `basePatterns` are the initial patterns to use.  A ManagedProject
     * has an implicit defining these initial patterns based on a setting for either Maven or Ivy style patterns.
     */
    def apply(name: String)(implicit basePatterns: Patterns): RepositoryType =
      apply(name, None, None, None)
    /**
     * Constructs this repository type with the given `name` and `hostname`.  `basePatterns` are the initial patterns to use.
     * A ManagedProject has an implicit defining these initial patterns based on a setting for either Maven or Ivy style patterns.
     */
    def apply(name: String, hostname: String)(implicit basePatterns: Patterns): RepositoryType =
      apply(name, Some(hostname), None, None)
    /**
     * Constructs this repository type with the given `name`, `hostname`, and the `basePath` against which the initial
     * patterns will be resolved.  `basePatterns` are the initial patterns to use.
     * A ManagedProject has an implicit defining these initial patterns based on a setting for either Maven or Ivy style patterns.
     */
    def apply(name: String, hostname: String, basePath: String)(implicit basePatterns: Patterns): RepositoryType =
      apply(name, Some(hostname), None, Some(basePath))
    /**
     * Constructs this repository type with the given `name`, `hostname`, and `port`.  `basePatterns` are the initial patterns to use.
     * A ManagedProject has an implicit defining these initial patterns based on a setting for either Maven or Ivy style patterns.
     */
    def apply(name: String, hostname: String, port: Int)(implicit basePatterns: Patterns): RepositoryType =
      apply(name, Some(hostname), Some(port), None)
    /**
     * Constructs this repository type with the given `name`, `hostname`, `port`, and the `basePath` against which the initial
     * patterns will be resolved.  `basePatterns` are the initial patterns to use.
     * A ManagedProject has an implicit defining these initial patterns based on a setting for either Maven or Ivy style patterns.
     */
    def apply(name: String, hostname: String, port: Int, basePath: String)(implicit basePatterns: Patterns): RepositoryType =
      apply(name, Some(hostname), Some(port), Some(basePath))
    /**
     * Constructs this repository type with the given `name`, `hostname`, `port`, and the `basePath` against which the initial
     * patterns will be resolved.  `basePatterns` are the initial patterns to use.  All but the `name` are optional (use None).
     * A ManagedProject has an implicit defining these initial patterns based on a setting for either Maven or Ivy style patterns.
     */
    def apply(name: String, hostname: Option[String], port: Option[Int], basePath: Option[String])(implicit basePatterns: Patterns): RepositoryType =
      construct(name, SshConnection(None, hostname, port), resolvePatterns(basePath, basePatterns))
  }
  /** A factory to construct an interface to an Ivy SSH resolver.*/
  object ssh extends Define[SshRepository] {
    protected def construct(name: String, connection: SshConnection, patterns: Patterns) = SshRepository(name, connection, patterns, None)
  }
  /** A factory to construct an interface to an Ivy SFTP resolver.*/
  object sftp extends Define[SftpRepository] {
    protected def construct(name: String, connection: SshConnection, patterns: Patterns) = SftpRepository(name, connection, patterns)
  }
  /** A factory to construct an interface to an Ivy filesystem resolver. */
  object file {
    /**
     * Constructs a file resolver with the given name.  The patterns to use must be explicitly specified
     * using the `ivys` or `artifacts` methods on the constructed resolver object.
     */
    def apply(name: String): FileRepository = FileRepository(name, defaultFileConfiguration, Patterns(false))
    /** Constructs a file resolver with the given name and base directory. */
    def apply(name: String, baseDirectory: File)(implicit basePatterns: Patterns): FileRepository =
      baseRepository(new File(baseDirectory.toURI.normalize) getAbsolutePath)(FileRepository(name, defaultFileConfiguration, _))
  }
  object url {
    /**
     * Constructs a URL resolver with the given name.  The patterns to use must be explicitly specified
     * using the `ivys` or `artifacts` methods on the constructed resolver object.
     */
    def apply(name: String): URLRepository = URLRepository(name, Patterns(false))
    /** Constructs a file resolver with the given name and base directory. */
    def apply(name: String, baseURL: URL)(implicit basePatterns: Patterns): URLRepository =
      baseRepository(baseURL.toURI.normalize.toString)(URLRepository(name, _))
  }
  private def baseRepository[T](base: String)(construct: Patterns => T)(implicit basePatterns: Patterns): T =
    construct(resolvePatterns(base, basePatterns))

  /**
   * If `base` is None, `patterns` is returned unchanged.
   * Otherwise, the ivy file and artifact patterns in `patterns` are resolved against the given base.
   */
  private def resolvePatterns(base: Option[String], patterns: Patterns): Patterns =
    base match {
      case Some(path) => resolvePatterns(path, patterns)
      case None       => patterns
    }
  /** Resolves the ivy file and artifact patterns in `patterns` against the given base. */
  private def resolvePatterns(base: String, basePatterns: Patterns): Patterns =
    {
      def resolveAll(patterns: Seq[String]) = patterns.map(p => resolvePattern(base, p))
      Patterns(resolveAll(basePatterns.ivyPatterns), resolveAll(basePatterns.artifactPatterns), basePatterns.isMavenCompatible, basePatterns.descriptorOptional, basePatterns.skipConsistencyCheck)
    }
  private[sbt] def resolvePattern(base: String, pattern: String): String =
    {
      val normBase = base.replace('\\', '/')
      if (normBase.endsWith("/") || pattern.startsWith("/")) normBase + pattern else normBase + "/" + pattern
    }
  def defaultFileConfiguration = FileConfiguration(true, None)
  def mavenStylePatterns = Patterns(Nil, mavenStyleBasePattern :: Nil, true)
  def ivyStylePatterns = defaultIvyPatterns //Patterns(Nil, Nil, false)

  def defaultPatterns = mavenStylePatterns
  def mavenStyleBasePattern = "[organisation]/[module](_[scalaVersion])(_[sbtVersion])/[revision]/[artifact]-[revision](-[classifier]).[ext]"
  def localBasePattern = "[organisation]/[module]/" + PluginPattern + "[revision]/[type]s/[artifact](-[classifier]).[ext]"
  def defaultRetrievePattern = "[type]s/[organisation]/[module]/" + PluginPattern + "[artifact](-[revision])(-[classifier]).[ext]"
  final val PluginPattern = "(scala_[scalaVersion]/)(sbt_[sbtVersion]/)"
  private[this] def mavenLocalDir: File = {
    def loadHomeFromSettings(f: () => File): Option[File] =
      try {
        val file = f()
        if (!file.exists) None
        else ((XML.loadFile(file) \ "localRepository").text match {
          case ""    => None
          case e @ _ => Some(new File(e))
        })
      } catch {
        // Occurs inside File constructor when property or environment variable does not exist
        case _: NullPointerException => None
        // Occurs when File does not exist
        case _: IOException          => None
        case e: SAXParseException    => System.err.println(s"WARNING: Problem parsing ${f().getAbsolutePath}, ${e.getMessage}"); None
      }
    loadHomeFromSettings(() => new File(Path.userHome, ".m2/settings.xml")) orElse
      loadHomeFromSettings(() => new File(new File(System.getenv("M2_HOME")), "conf/settings.xml")) getOrElse
      new File(Path.userHome, ".m2/repository")
  }
  // TODO - should this just be the *exact* same as mavenLocal?  probably...
  def publishMavenLocal: MavenCache = new MavenCache("publish-m2-local", mavenLocalDir)
  def mavenLocal: MavenRepository = new MavenCache("Maven2 Local", mavenLocalDir)
  def defaultLocal = defaultUserFileRepository("local")
  def defaultShared = defaultUserFileRepository("shared")
  def defaultUserFileRepository(id: String) =
    {
      val pList = ("${ivy.home}/" + id + "/" + localBasePattern) :: Nil
      FileRepository(id, defaultFileConfiguration, Patterns(pList, pList, false))
    }
  def defaultIvyPatterns =
    {
      val pList = List(localBasePattern)
      Patterns(pList, pList, false)
    }
}
