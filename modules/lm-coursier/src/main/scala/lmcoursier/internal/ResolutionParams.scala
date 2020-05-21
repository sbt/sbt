package lmcoursier.internal

import java.io.File

import coursier.cache.{CacheLogger, FileCache}
import coursier.ProjectCache
import coursier.core._
import coursier.params.rule.Strict
import lmcoursier.FallbackDependency
import lmcoursier.definitions.ToCoursier
import coursier.util.Task

import scala.collection.mutable

// private[coursier]
final case class ResolutionParams(
  dependencies: Seq[(Configuration, Dependency)],
  fallbackDependencies: Seq[FallbackDependency],
  orderedConfigs: Seq[(Configuration, Seq[Configuration])],
  subConfigs: Seq[(Configuration, Configuration)],
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
  params: coursier.params.ResolutionParams,
  strictOpt: Option[Strict],
  missingOk: Boolean,
) {

  lazy val allConfigExtends: Map[Configuration, Set[Configuration]] = {
    val map = new mutable.HashMap[Configuration, Set[Configuration]]
    val subConfigMap = subConfigs
      .map { case (config, parent) => parent -> config }
      .groupBy(_._1)
      .mapValues(_.map(_._2))
      .toMap
    for ((config, extends0) <- orderedConfigs) {
      val allExtends = extends0
        .iterator
        // the else of the getOrElse shouldn't be hit (because of the ordering of the configurations)
        .foldLeft(Set(config))((acc, ext) => acc ++ map.getOrElse(ext, Set(ext)))
      val viaSubConfig = subConfigMap.getOrElse(config, Nil)
      map += config -> (allExtends ++ viaSubConfig)
    }
    map.toMap
  }

  val fallbackDependenciesRepositories =
    if (fallbackDependencies.isEmpty)
      Nil
    else {
      val map = fallbackDependencies
        .map { dep =>
          (ToCoursier.module(dep.module), dep.version) -> ((dep.url, dep.changing))
        }
        .toMap

      Seq(
        TemporaryInMemoryRepository(map, cache)
      )
    }

  lazy val resolutionKey = {
    val cleanCache = cache
      .withPool(null)
      .withLogger(null)
      .withSync(null)
    SbtCoursierCache.ResolutionKey(
      dependencies,
      internalRepositories,
      mainRepositories,
      fallbackDependenciesRepositories,
      copy(
        parentProjectCache = Map.empty,
        loggerOpt = None,
        parallel = 0,
        cache = cleanCache
      ),
      cleanCache,
      missingOk
    )
  }

  override lazy val hashCode =
    ResolutionParams.unapply(this).get.##

}

// private[coursier]
object ResolutionParams {

  def defaultIvyProperties(ivyHomeOpt: Option[File]): Map[String, String] = {

    val ivyHome = sys.props
      .get("ivy.home")
      .orElse(ivyHomeOpt.map(_.getAbsoluteFile.toURI.getPath))
      .getOrElse(new File(sys.props("user.home")).toURI.getPath + ".ivy2")

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
