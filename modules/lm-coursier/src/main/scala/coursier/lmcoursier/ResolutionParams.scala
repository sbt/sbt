package coursier.lmcoursier

import java.io.File
import java.net.URL

import coursier.{Cache, CachePolicy, FallbackDependenciesRepository, ProjectCache, Resolution, moduleNameString}
import coursier.core._
import coursier.extra.Typelevel
import coursier.ivy.PropertiesPattern
import sbt.librarymanagement.{Resolver, URLRepository}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.Duration

final case class ResolutionParams(
  dependencies: Seq[(Configuration, Dependency)],
  fallbackDependencies: Seq[(Module, String, URL, Boolean)],
  configGraphs: Seq[Set[Configuration]],
  autoScalaLib: Boolean,
  mainRepositories: Seq[Repository],
  parentProjectCache: ProjectCache,
  interProjectDependencies: Seq[Project],
  internalRepositories: Seq[Repository],
  userEnabledProfiles: Set[String],
  userForceVersions: Map[Module, String],
  typelevel: Boolean,
  so: Organization,
  sv: String,
  sbtClassifiers: Boolean,
  parallelDownloads: Int,
  projectName: String,
  maxIterations: Int,
  createLogger: () => Cache.Logger,
  cache: File,
  cachePolicies: Seq[CachePolicy],
  ttl: Option[Duration],
  checksums: Seq[Option[String]]
) {

  val fallbackDependenciesRepositories =
    if (fallbackDependencies.isEmpty)
      Nil
    else {
      val map = fallbackDependencies.map {
        case (mod, ver, url, changing) =>
          (mod, ver) -> ((url, changing))
      }.toMap

      Seq(
        FallbackDependenciesRepository(map)
      )
    }

  val repositories =
    internalRepositories ++
      mainRepositories ++
      fallbackDependenciesRepositories

  private val noOptionalFilter: Option[Dependency => Boolean] = Some(dep => !dep.optional)
  private val typelevelOrgSwap: Option[Dependency => Dependency] = Some(Typelevel.swap(_))

  private def forcedScalaModules(
    scalaOrganization: Organization,
    scalaVersion: String
  ): Map[Module, String] =
    Map(
      Module(scalaOrganization, name"scala-library", Map.empty) -> scalaVersion,
      Module(scalaOrganization, name"scala-compiler", Map.empty) -> scalaVersion,
      Module(scalaOrganization, name"scala-reflect", Map.empty) -> scalaVersion,
      Module(scalaOrganization, name"scalap", Map.empty) -> scalaVersion
    )

  private def startRes(configs: Set[Configuration]) = Resolution(
    dependencies
      .collect {
        case (config, dep) if configs(config) =>
          dep
      }
      .toSet,
    filter = noOptionalFilter,
    userActivations =
      if (userEnabledProfiles.isEmpty)
        None
      else
        Some(userEnabledProfiles.iterator.map(_ -> true).toMap),
    forceVersions =
      // order matters here
      userForceVersions ++
        (if (autoScalaLib && (configs(Configuration.compile) || configs(Configuration("scala-tool")))) forcedScalaModules(so, sv) else Map()) ++
        interProjectDependencies.map(_.moduleVersion),
    projectCache = parentProjectCache,
    mapDependencies = if (typelevel && (configs(Configuration.compile) || configs(Configuration("scala-tool")))) typelevelOrgSwap else None
  )

  lazy val allStartRes = configGraphs.map(configs => configs -> startRes(configs)).toMap

  lazy val resolutionKey = SbtCoursierCache.ResolutionKey(
    dependencies,
    repositories,
    userEnabledProfiles,
    allStartRes,
    sbtClassifiers
  )

}

object ResolutionParams {

  def defaultIvyProperties(): Map[String, String] = {

    val ivyHome = sys.props.getOrElse(
      "ivy.home",
      new File(sys.props("user.home")).toURI.getPath + ".ivy2"
    )

    val sbtIvyHome = sys.props.getOrElse(
      "sbt.ivy.home",
      ivyHome
    )

    Map(
      "ivy.home" -> ivyHome,
      "sbt.ivy.home" -> sbtIvyHome
    ) ++ sys.props
  }

  private def exceptionPatternParser(): String => coursier.ivy.Pattern = {

    val props = sys.props.toMap

    val extraProps = new ArrayBuffer[(String, String)]

    def addUriProp(key: String): Unit =
      for (b <- props.get(key)) {
        val uri = new File(b).toURI.toString
        extraProps += s"$key.uri" -> uri
      }

    addUriProp("sbt.global.base")
    addUriProp("user.home")

    {
      s =>
        val p = PropertiesPattern.parse(s) match {
          case Left(err) =>
            throw new Exception(s"Cannot parse pattern $s: $err")
          case Right(p) =>
            p
        }

        p.substituteProperties(props ++ extraProps) match {
          case Left(err) =>
            throw new Exception(err)
          case Right(p) =>
            p
        }
    }
  }

  def globalPluginPatterns(sbtVersion: String): Seq[coursier.ivy.Pattern] = {

    val defaultRawPattern = s"$${sbt.global.base.uri-$${user.home.uri}/.sbt/$sbtVersion}/plugins/target" +
      "/resolution-cache/" +
      "[organization]/[module](/scala_[scalaVersion])(/sbt_[sbtVersion])/[revision]/resolved.xml.[ext]"

    // seems to be required in more recent versions of sbt (since 0.13.16?)
    val extraRawPattern = s"$${sbt.global.base.uri-$${user.home.uri}/.sbt/$sbtVersion}/plugins/target" +
      "(/scala-[scalaVersion])(/sbt-[sbtVersion])" +
      "/resolution-cache/" +
      "[organization]/[module](/scala_[scalaVersion])(/sbt_[sbtVersion])/[revision]/resolved.xml.[ext]"

    val p = exceptionPatternParser()

    Seq(
      defaultRawPattern,
      extraRawPattern
    ).map(p)
  }

  private val slowReposBase = Seq(
    "https://repo.typesafe.com/",
    "https://repo.scala-sbt.org/",
    "http://repo.typesafe.com/",
    "http://repo.scala-sbt.org/"
  )

  private val fastReposBase = Seq(
    "http://repo1.maven.org/",
    "https://repo1.maven.org/"
  )

  private def url(res: Resolver): Option[String] =
    res match {
      case m: sbt.librarymanagement.MavenRepository =>
        Some(m.root)
      case u: URLRepository =>
        u.patterns.artifactPatterns.headOption
          .orElse(u.patterns.ivyPatterns.headOption)
      case _ =>
        None
    }

  private def fastRepo(res: Resolver): Boolean =
    url(res).exists(u => fastReposBase.exists(u.startsWith))

  private def slowRepo(res: Resolver): Boolean =
    url(res).exists(u => slowReposBase.exists(u.startsWith))

  def reorderResolvers(resolvers: Seq[Resolver]): Seq[Resolver] =
    if (resolvers.exists(fastRepo) && resolvers.exists(slowRepo)) {
      val (slow, other) = resolvers.partition(slowRepo)
      other ++ slow
    } else
      resolvers

}
