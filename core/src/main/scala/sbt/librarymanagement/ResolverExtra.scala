/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt.librarymanagement

import java.io.{ File, IOException }
import java.net.{ URI, URL }
import scala.annotation.nowarn
import scala.xml.XML
import org.xml.sax.SAXParseException
import sbt.util.Logger

import java.net.URI
import scala.util.matching.Regex

final class RawRepository(val resolver: AnyRef, name: String) extends Resolver(name) {
  override def toString = "Raw(" + resolver.toString + ")"

  override def equals(o: Any): Boolean = o match {
    case o: RawRepository =>
      this.name == o.name
    case _ => false
  }

  override def hashCode: Int = {
    var hash = 1
    hash = hash * 31 + this.name.##
    hash
  }
}

private[librarymanagement] abstract class MavenRepositoryFunctions {
  def apply(name: String, root: String, localIfFile: Boolean = true): MavenRepository =
    MavenRepo(name, root, localIfFile)
}

private[librarymanagement] abstract class PatternsFunctions {
  implicit def defaultPatterns: Patterns = Resolver.defaultPatterns

  def apply(artifactPatterns: String*): Patterns = Patterns(true, artifactPatterns: _*)
  def apply(isMavenCompatible: Boolean, artifactPatterns: String*): Patterns = {
    val patterns = artifactPatterns.toVector
    Patterns()
      .withIvyPatterns(patterns)
      .withArtifactPatterns(patterns)
      .withIsMavenCompatible(isMavenCompatible)
  }
}

private[librarymanagement] trait SshBasedRepositoryExtra {

  /** The object representing the configured ssh connection for this repository. */
  def connection: SshConnection

  type RepositoryType <: SshBasedRepository
  protected def copy(connection: SshConnection): RepositoryType
  private def copy(authentication: SshAuthentication): RepositoryType =
    copy(connection withAuthentication authentication)

  /** Configures this to use the specified user name and password when connecting to the remote repository. */
  def as(user: String, password: String): RepositoryType = as(user, Some(password))
  def as(user: String): RepositoryType = as(user, None)
  def as(user: String, password: Option[String]): RepositoryType =
    copy(PasswordAuthentication(user, password))

  /** Configures this to use the specified keyfile and password for the keyfile when connecting to the remote repository. */
  def as(user: String, keyfile: File): RepositoryType = as(user, keyfile, None)
  def as(user: String, keyfile: File, password: String): RepositoryType =
    as(user, keyfile, Some(password))

  def as(user: String, keyfile: File, password: Option[String]): RepositoryType =
    copy(KeyFileAuthentication(user, keyfile, password))
}

private[librarymanagement] trait SshRepositoryExtra extends SshBasedRepositoryExtra {
  def name: String
  def patterns: sbt.librarymanagement.Patterns
  def publishPermissions: Option[String]

  type RepositoryType = SshRepository

  protected def copy(connection: SshConnection): SshRepository =
    SshRepository(name, connection, patterns, publishPermissions)
}

private[librarymanagement] trait SftpRepositoryExtra extends SshBasedRepositoryExtra {
  def name: String
  def patterns: sbt.librarymanagement.Patterns

  type RepositoryType = SftpRepository

  protected def copy(connection: SshConnection): SftpRepository =
    SftpRepository(name, connection, patterns)
}

/** A repository that conforms to sbt launcher's interface */
private[sbt] class FakeRepository(resolver: AnyRef, name: String) extends xsbti.Repository {
  def rawRepository = new RawRepository(resolver, name)
}

private[librarymanagement] abstract class ResolverFunctions {
  import sbt.internal.librarymanagement.LMSysProp.useSecureResolvers

  val TypesafeRepositoryRoot = typesafeRepositoryRoot(useSecureResolvers)
  val SbtRepositoryRoot = sbtRepositoryRoot(useSecureResolvers)
  @deprecated("Renamed to SbtRepositoryRoot.", "1.0.0")
  val SbtPluginRepositoryRoot = SbtRepositoryRoot
  val SonatypeRepositoryRoot = "https://oss.sonatype.org/content/repositories"
  val SonatypeS01RepositoryRoot = "https://s01.oss.sonatype.org/content/repositories"
  val SonatypeReleasesRepository =
    "https://oss.sonatype.org/service/local/repositories/releases/content/"
  val JavaNet2RepositoryName = "java.net Maven2 Repository"
  val JavaNet2RepositoryRoot = javanet2RepositoryRoot(useSecureResolvers)
  val JCenterRepositoryName = "jcenter"
  val JCenterRepositoryRoot = "https://jcenter.bintray.com/"
  val DefaultMavenRepositoryRoot = "https://repo1.maven.org/maven2/"
  val DefaultMavenRepository =
    MavenRepository("public", centralRepositoryRoot(useSecureResolvers))
  val JavaNet2Repository = MavenRepository(JavaNet2RepositoryName, JavaNet2RepositoryRoot)
  val JCenterRepository = MavenRepository(JCenterRepositoryName, JCenterRepositoryRoot)

  def mavenCentral: Resolver = DefaultMavenRepository
  def defaults: Vector[Resolver] = Vector(mavenCentral)

  // TODO: This switch is only kept for backward compatibility. Hardcode to HTTPS in the future.
  private[sbt] def centralRepositoryRoot(secure: Boolean) =
    (if (secure) "https" else "http") + "://repo1.maven.org/maven2/"
  // TODO: This switch is only kept for backward compatibility. Hardcode to HTTPS in the future.
  private[sbt] def javanet2RepositoryRoot(secure: Boolean) =
    if (secure) "https://maven.java.net/content/repositories/public/"
    else "http://download.java.net/maven/2"
  // TODO: This switch is only kept for backward compatibility. Hardcode to HTTPS in the future.
  private[sbt] def typesafeRepositoryRoot(secure: Boolean) =
    (if (secure) "https" else "http") + "://repo.typesafe.com/typesafe"
  // TODO: This switch is only kept for backward compatibility. Hardcode to HTTPS in the future.
  private[sbt] def sbtRepositoryRoot(secure: Boolean) =
    (if (secure) "https" else "http") + "://repo.scala-sbt.org/scalasbt"

  // obsolete: kept only for launcher compatibility
  private[sbt] val ScalaToolsReleasesName = "Sonatype OSS Releases"
  private[sbt] val ScalaToolsSnapshotsName = "Sonatype OSS Snapshots"
  private[sbt] val ScalaToolsReleasesRoot = SonatypeReleasesRepository
  private[sbt] val ScalaToolsSnapshotsRoot = SonatypeRepositoryRoot + "/snapshots"
  private[sbt] val ScalaToolsReleases =
    MavenRepository(ScalaToolsReleasesName, ScalaToolsReleasesRoot)
  private[sbt] val ScalaToolsSnapshots =
    MavenRepository(ScalaToolsSnapshotsName, ScalaToolsSnapshotsRoot)

  def typesafeRepo(status: String) =
    MavenRepository("typesafe-" + status, TypesafeRepositoryRoot + "/" + status)
  def typesafeIvyRepo(status: String) =
    url("typesafe-ivy-" + status, new URI(TypesafeRepositoryRoot + "/ivy-" + status + "/").toURL)(
      ivyStylePatterns
    )
  def sbtIvyRepo(status: String) =
    url(s"sbt-ivy-$status", new URI(s"$SbtRepositoryRoot/ivy-$status/").toURL)(ivyStylePatterns)
  def sbtPluginRepo(status: String) =
    url("sbt-plugin-" + status, new URI(SbtRepositoryRoot + "/sbt-plugin-" + status + "/").toURL)(
      ivyStylePatterns
    )
  @deprecated(
    """Use sonatypeOssRepos instead e.g. `resolvers ++= Resolver.sonatypeOssRepos("snapshots")`""",
    "1.7.0"
  )
  def sonatypeRepo(status: String) =
    MavenRepository(
      "sonatype-" + status,
      if (status == "releases") SonatypeReleasesRepository
      else SonatypeRepositoryRoot + "/" + status
    )
  private def sonatypeS01Repo(status: String) =
    MavenRepository(
      "sonatype-s01-" + status,
      SonatypeS01RepositoryRoot + "/" + status
    )
  def sonatypeOssRepos(status: String) =
    Vector(sonatypeRepo(status): @nowarn("cat=deprecation"), sonatypeS01Repo(status))
  def bintrayRepo(owner: String, repo: String) =
    MavenRepository(s"bintray-$owner-$repo", s"https://dl.bintray.com/$owner/$repo/")
  def bintrayIvyRepo(owner: String, repo: String) =
    url(s"bintray-$owner-$repo", new URI(s"https://dl.bintray.com/$owner/$repo/").toURL)(
      Resolver.ivyStylePatterns
    )
  def jcenterRepo = JCenterRepository

  val ApacheMavenSnapshotsRepo = MavenRepository(
    "apache-snapshots",
    "https://repository.apache.org/content/repositories/snapshots/"
  )

  val ApacheMavenStagingRepo = MavenRepository(
    "apache-staging",
    "https://repository.apache.org/content/groups/staging/"
  )

  /** Add the local and Maven Central repositories to the user repositories. */
  def combineDefaultResolvers(userResolvers: Vector[Resolver]): Vector[Resolver] =
    combineDefaultResolvers(userResolvers, mavenCentral = true)

  /**
   * Add the local Ivy repository to the user repositories.
   * If `mavenCentral` is true, add the Maven Central repository.
   */
  def combineDefaultResolvers(
      userResolvers: Vector[Resolver],
      mavenCentral: Boolean
  ): Vector[Resolver] =
    combineDefaultResolvers(userResolvers, jcenter = false, mavenCentral)

  /**
   * Add the local Ivy repository to the user repositories.
   * If `jcenter` is true, add the JCenter.
   * If `mavenCentral` is true, add the Maven Central repository.
   */
  def combineDefaultResolvers(
      userResolvers: Vector[Resolver],
      jcenter: Boolean,
      mavenCentral: Boolean
  ): Vector[Resolver] =
    Vector(Resolver.defaultLocal) ++
      userResolvers ++
      single(JCenterRepository, jcenter) ++
      single(DefaultMavenRepository, mavenCentral)

  /**
   * Reorganize the built-in resolvers that is configured for this application by the sbt launcher.
   * If `jcenter` is true, add the JCenter.
   * If `mavenCentral` is true, add the Maven Central repository.
   */
  private[sbt] def reorganizeAppResolvers(
      appResolvers: Vector[Resolver],
      jcenter: Boolean,
      mavenCentral: Boolean
  ): Vector[Resolver] =
    appResolvers.partition(_ == Resolver.defaultLocal) match {
      case (locals, xs) =>
        locals ++
          (xs.partition(_ == JCenterRepository) match {
            case (_, xs) =>
              single(JCenterRepository, jcenter) ++
                (xs.partition(_ == DefaultMavenRepository) match {
                  case (_, xs) =>
                    single(
                      DefaultMavenRepository,
                      mavenCentral
                    ) ++ xs // TODO - Do we need to filter out duplicates?
                })
          })
    }

  private def single[T](value: T, nonEmpty: Boolean): Vector[T] =
    if (nonEmpty) Vector(value) else Vector.empty

  /** A base class for defining factories for interfaces to Ivy repositories that require a hostname , port, and patterns. */
  sealed abstract class Define[RepositoryType <: SshBasedRepository] {

    /** Subclasses should implement this method to */
    protected def construct(
        name: String,
        connection: SshConnection,
        patterns: Patterns
    ): RepositoryType

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
    def apply(name: String, hostname: String, basePath: String)(implicit
        basePatterns: Patterns
    ): RepositoryType =
      apply(name, Some(hostname), None, Some(basePath))

    /**
     * Constructs this repository type with the given `name`, `hostname`, and `port`.  `basePatterns` are the initial patterns to use.
     * A ManagedProject has an implicit defining these initial patterns based on a setting for either Maven or Ivy style patterns.
     */
    def apply(name: String, hostname: String, port: Int)(implicit
        basePatterns: Patterns
    ): RepositoryType =
      apply(name, Some(hostname), Some(port), None)

    /**
     * Constructs this repository type with the given `name`, `hostname`, `port`, and the `basePath` against which the initial
     * patterns will be resolved.  `basePatterns` are the initial patterns to use.
     * A ManagedProject has an implicit defining these initial patterns based on a setting for either Maven or Ivy style patterns.
     */
    def apply(name: String, hostname: String, port: Int, basePath: String)(implicit
        basePatterns: Patterns
    ): RepositoryType =
      apply(name, Some(hostname), Some(port), Some(basePath))

    /**
     * Constructs this repository type with the given `name`, `hostname`, `port`, and the `basePath` against which the initial
     * patterns will be resolved.  `basePatterns` are the initial patterns to use.  All but the `name` are optional (use None).
     * A ManagedProject has an implicit defining these initial patterns based on a setting for either Maven or Ivy style patterns.
     */
    def apply(name: String, hostname: Option[String], port: Option[Int], basePath: Option[String])(
        implicit basePatterns: Patterns
    ): RepositoryType =
      construct(name, SshConnection(None, hostname, port), resolvePatterns(basePath, basePatterns))
  }

  /** A factory to construct an interface to an Ivy SSH resolver. */
  object ssh extends Define[SshRepository] {
    protected def construct(name: String, connection: SshConnection, patterns: Patterns) =
      SshRepository(name, connection, patterns, None)
  }

  /** A factory to construct an interface to an Ivy SFTP resolver. */
  object sftp extends Define[SftpRepository] {
    protected def construct(name: String, connection: SshConnection, patterns: Patterns) =
      SftpRepository(name, connection, patterns)
  }

  /** A factory to construct an interface to an Ivy filesystem resolver. */
  object file {

    /**
     * Constructs a file resolver with the given name.  The patterns to use must be explicitly specified
     * using the `withPatterns` method on the constructed resolver object.
     */
    def apply(name: String): FileRepository =
      FileRepository(name, defaultFileConfiguration, Patterns(false))

    /** Constructs a file resolver with the given name and base directory. */
    def apply(name: String, baseDirectory: File)(implicit basePatterns: Patterns): FileRepository =
      baseRepository(
        new File(toUri(baseDirectory).normalize.getSchemeSpecificPart).getAbsolutePath
      )(
        FileRepository(name, defaultFileConfiguration, _)
      )

    private def toUri(dir: File): URI = dir.toPath.toUri
  }
  object url {

    /**
     * Constructs a URL resolver with the given name.  The patterns to use must be explicitly specified
     * using the `withPatterns` method on the constructed resolver object.
     */
    def apply(name: String): URLRepository = URLRepository(name, Patterns(false))

    /** Constructs a file resolver with the given name and base directory. */
    def apply(name: String, baseURL: URL)(implicit basePatterns: Patterns): URLRepository =
      baseRepository(baseURL.toURI.normalize.toString)(URLRepository(name, _))
  }
  private def baseRepository[T](base: String)(construct: Patterns => T)(implicit
      basePatterns: Patterns
  ): T =
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
  private def resolvePatterns(base: String, basePatterns: Patterns): Patterns = {
    def resolveAll(patterns: Vector[String]) = patterns.map(p => resolvePattern(base, p))
    Patterns(
      resolveAll(basePatterns.ivyPatterns),
      resolveAll(basePatterns.artifactPatterns),
      basePatterns.isMavenCompatible,
      basePatterns.descriptorOptional,
      basePatterns.skipConsistencyCheck
    )
  }
  private[sbt] def resolvePattern(base: String, pattern: String): String = {
    val normBase = base.replace('\\', '/')
    if (normBase.endsWith("/") || pattern.startsWith("/")) normBase + pattern
    else normBase + "/" + pattern
  }
  def defaultFileConfiguration = FileConfiguration(true, None)
  def mavenStylePatterns = Patterns().withArtifactPatterns(Vector(mavenStyleBasePattern))
  def ivyStylePatterns = defaultIvyPatterns // Patterns(Nil, Nil, false)

  def defaultPatterns = mavenStylePatterns
  def mavenStyleBasePattern =
    "[organisation]/[module](_[scalaVersion])(_[sbtVersion])/[revision]/[artifact]-[revision](-[classifier]).[ext]"
  def localBasePattern =
    "[organisation]/[module]/" + PluginPattern + "([branch]/)[revision]/[type]s/[artifact](-[classifier]).[ext]"
  def defaultRetrievePattern =
    "[type]s/[organisation]/[module]/" + PluginPattern + "[artifact](-[revision])(-[classifier]).[ext]"
  final val PluginPattern = "(scala_[scalaVersion]/)(sbt_[sbtVersion]/)"

  private[librarymanagement] def expandMavenSettings(
      str: String,
      envVars: Map[String, String] = sys.env,
      props: Map[String, String] = sys.props.toMap
  ): String = {
    // Aren't regular expressions beautifully clear and concise.
    // This means "find all ${...}" blocks, with the first group of each being the text between curly brackets.
    val findQuoted = "\\$\\{([^}]*)}".r
    val env = "env\\.(.*)".r

    findQuoted.replaceAllIn(
      str,
      regexMatch =>
        Regex.quoteReplacement {
          regexMatch.group(1) match {
            case env(variable) => envVars.getOrElse(variable, "")
            case property      => props.getOrElse(property, "")
          }
        }
    )
  }

  private[this] def mavenLocalDir: File = {
    def loadHomeFromSettings(f: () => File): Option[File] =
      try {
        val file = f()
        if (!file.exists) None
        else
          ((XML.loadFile(file) \ "localRepository").text match {
            case ""    => None
            case e @ _ => Some(new File(expandMavenSettings(e)))
          })
      } catch {
        // Occurs inside File constructor when property or environment variable does not exist
        case _: NullPointerException => None
        // Occurs when File does not exist
        case _: IOException => None
        case e: SAXParseException =>
          System.err.println(s"WARNING: Problem parsing ${f().getAbsolutePath}, ${e.getMessage}");
          None
      }
    sys.props.get("maven.repo.local").map(new File(_)) orElse
      loadHomeFromSettings(() => new File(sbt.io.Path.userHome, ".m2/settings.xml")) orElse
      loadHomeFromSettings(() =>
        new File(new File(System.getenv("M2_HOME")), "conf/settings.xml")
      ) getOrElse
      new File(sbt.io.Path.userHome, ".m2/repository")
  }
  // TODO - should this just be the *exact* same as mavenLocal?  probably...
  def publishMavenLocal: MavenCache = new MavenCache("publish-m2-local", mavenLocalDir)
  def mavenLocal: MavenRepository = new MavenCache("Maven2 Local", mavenLocalDir)
  def defaultLocal = defaultUserFileRepository("local")
  def defaultShared = defaultUserFileRepository("shared")
  def defaultUserFileRepository(id: String) = {
    val pList = Vector(s"$${ivy.home}/$id/$localBasePattern")
    FileRepository(
      id,
      defaultFileConfiguration,
      Patterns().withIvyPatterns(pList).withArtifactPatterns(pList).withIsMavenCompatible(false)
    )
  }
  def defaultIvyPatterns = {
    val pList = Vector(localBasePattern)
    Patterns().withIvyPatterns(pList).withArtifactPatterns(pList).withIsMavenCompatible(false)
  }

  // to display all error messages at once, just log here don't throw
  private[sbt] def warnHttp(value: String, suggestion: String, logger: Logger): Unit = {
    logger.error(s"insecure HTTP request is unsupported '$value'; switch to HTTPS$suggestion")
  }
  private[sbt] def isInsecureUrl(str: String): Boolean = {
    // don't try to parse str as URL because it could contain $variable from Ivy pattern
    str.startsWith("http:") &&
    !(str.startsWith("http://localhost/")
      || str.startsWith("http://localhost:")
      || str.startsWith("http://127.0.0.1/")
      || str.startsWith("http://127.0.0.1:"))
  }
  private[sbt] def validateURLRepository(repo: URLRepository, logger: Logger): Boolean = {
    if (repo.allowInsecureProtocol) false
    else {
      val patterns = repo.patterns
      val ivy = patterns.ivyPatterns.headOption match {
        case Some(x) => isInsecureUrl(x)
        case _       => false
      }
      val art = patterns.artifactPatterns.headOption match {
        case Some(x) => isInsecureUrl(x)
        case _       => false
      }
      if (ivy || art) {
        warnHttp(
          patterns.toString,
          s""" or opt-in as Resolver.url("${repo.name}", url(...)).withAllowInsecureProtocol(true), or by using allowInsecureProtocol in repositories file""",
          logger
        )
        true
      } else false
    }
  }

  private[sbt] def validateMavenRepo(repo: MavenRepo, logger: Logger): Boolean =
    if (repo.allowInsecureProtocol) false
    else if (isInsecureUrl(repo.root)) {
      warnHttp(
        repo.root,
        s""" or opt-in as ("${repo.name}" at "${repo.root}").withAllowInsecureProtocol(true), or by using allowInsecureProtocol in repositories file""",
        logger
      )
      true
    } else false

  private[sbt] def validateArtifact(art: Artifact, logger: Logger): Boolean =
    if (art.allowInsecureProtocol) false
    else
      art.url match {
        case Some(url) if isInsecureUrl(url.toString) =>
          warnHttp(
            art.toString,
            " or opt-in using from(url(...), allowInsecureProtocol = true) on ModuleID or .withAllowInsecureProtocol(true) on Artifact",
            logger
          )
          true
        case _ => false
      }
}
