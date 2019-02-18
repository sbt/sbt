package coursier.lmcoursier

import java.io.File

import coursier.cache.CacheLogger
import coursier.{FallbackDependenciesRepository, ProjectCache, Resolution, moduleNameString}
import coursier.core._
import coursier.extra.Typelevel
import coursier.ivy.PropertiesPattern
import sbt.librarymanagement.{Resolver, URLRepository}

import scala.collection.mutable.ArrayBuffer

final case class ResolutionParams(
  dependencies: Seq[(Configuration, Dependency)],
  fallbackDependencies: Seq[FallbackDependency],
  configGraphs: Seq[Set[Configuration]],
  autoScalaLibOpt: Option[(Organization, String)],
  mainRepositories: Seq[Repository],
  parentProjectCache: ProjectCache,
  interProjectDependencies: Seq[Project],
  internalRepositories: Seq[Repository],
  typelevel: Boolean,
  sbtClassifiers: Boolean,
  projectName: String,
  logger: CacheLogger,
  cacheParams: coursier.params.CacheParams,
  params: coursier.params.ResolutionParams
) {

  val fallbackDependenciesRepositories =
    if (fallbackDependencies.isEmpty)
      Nil
    else {
      val map = fallbackDependencies.map {
        case FallbackDependency(mod, ver, url, changing) =>
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
      },
    filter = noOptionalFilter,
    userActivations =
      if (params.profiles.isEmpty)
        None
      else
        Some(params.profiles.iterator.map(_ -> true).toMap),
    forceVersions =
      // order matters here
      params.forceVersion ++
        autoScalaLibOpt
          .filter(_ => configs(Configuration.compile) || configs(Configuration("scala-tool")))
          .map { case (so, sv) => forcedScalaModules(so, sv) }
          .getOrElse(Map.empty) ++
        interProjectDependencies.map(_.moduleVersion),
    projectCache = parentProjectCache,
    mapDependencies = if (typelevel && (configs(Configuration.compile) || configs(Configuration("scala-tool")))) typelevelOrgSwap else None
  )

  lazy val allStartRes = configGraphs.map(configs => configs -> startRes(configs)).toMap

  lazy val resolutionKey = SbtCoursierCache.ResolutionKey(
    dependencies,
    repositories,
    params.profiles,
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
