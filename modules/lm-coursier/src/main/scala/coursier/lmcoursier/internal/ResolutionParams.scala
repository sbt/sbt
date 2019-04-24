package coursier.lmcoursier.internal

import java.io.File

import coursier.cache.{CacheLogger, FileCache}
import coursier.ProjectCache
import coursier.core._
import coursier.lmcoursier.FallbackDependency
import coursier.util.{InMemoryRepository, Task}

private[coursier] final case class ResolutionParams(
  dependencies: Seq[(Configuration, Dependency)],
  fallbackDependencies: Seq[FallbackDependency],
  configGraphs: Seq[Set[Configuration]],
  autoScalaLibOpt: Option[(Organization, String)],
  mainRepositories: Seq[Repository],
  parentProjectCache: ProjectCache,
  interProjectDependencies: Seq[Project],
  internalRepositories: Seq[Repository],
  sbtClassifiers: Boolean,
  projectName: String,
  loggerOpt: Option[CacheLogger],
  cache: coursier.cache.FileCache[Task],
  parallel: Int,
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
        InMemoryRepository(map)
      )
    }

  val repositories =
    internalRepositories ++
      mainRepositories ++
      fallbackDependenciesRepositories

  lazy val resolutionKey = SbtCoursierCache.ResolutionKey(
    dependencies,
    repositories,
    copy(
      parentProjectCache = Map.empty,
      loggerOpt = None,
      cache = null, // temporary, until we can use https://github.com/coursier/coursier/pull/1090
      parallel = 0
    ),
    ResolutionParams.cacheKey {
      cache
        .withPool(null)
        .withLogger(null)
        .withSync[Task](null)
    },
    sbtClassifiers
  )

  override lazy val hashCode =
    ResolutionParams.unapply(this).get.##

}

private[coursier] object ResolutionParams {

  private lazy val m = {
    val cls = classOf[FileCache[Task]]
    //cls.getDeclaredMethods.foreach(println)
    val m = cls.getDeclaredMethod("params")
    m.setAccessible(true)
    m
  }

  // temporary, until we can use https://github.com/coursier/coursier/pull/1090
  private def cacheKey(cache: FileCache[Task]): Object =
    m.invoke(cache)

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

}
