package lmcoursier.internal

import java.util.concurrent.ConcurrentHashMap

import coursier.core._
import sbt.librarymanagement.UpdateReport
import coursier.cache.FileCache
import coursier.util.Task

// private[coursier]
class SbtCoursierCache {

  import SbtCoursierCache._

  private val resolutionsCache = new ConcurrentHashMap[ResolutionKey, Map[Configuration, Resolution]]
  // these may actually not need to be cached any more, now that the resolutions
  // are cached
  private val reportsCache = new ConcurrentHashMap[ReportKey, UpdateReport]


  def resolutionOpt(key: ResolutionKey): Option[Map[Configuration, Resolution]] =
    Option(resolutionsCache.get(key))
  def putResolution(key: ResolutionKey, res: Map[Configuration, Resolution]): Unit =
    resolutionsCache.put(key, res)

  def reportOpt(key: ReportKey): Option[UpdateReport] =
    Option(reportsCache.get(key))
  def putReport(key: ReportKey, report: UpdateReport): Unit =
    reportsCache.put(key, report)

  def clear(): Unit = {
    resolutionsCache.clear()
    reportsCache.clear()
  }

  def isEmpty: Boolean =
    resolutionsCache.isEmpty && reportsCache.isEmpty

}

// private[coursier]
object SbtCoursierCache {

  final case class ResolutionKey(
    dependencies: Seq[(Configuration, Dependency)],
    internalRepositories: Seq[Repository],
    mainRepositories: Seq[Repository],
    fallbackRepositories: Seq[Repository],
    params: ResolutionParams,
    cache: FileCache[Task],
    sbtClassifiers: Boolean
  )

  final case class ReportKey(
    dependencies: Seq[(Configuration, Dependency)],
    resolution: Map[Configuration, Resolution],
    withClassifiers: Boolean,
    sbtClassifiers: Boolean,
    includeSignatures: Boolean
  )


  // private[coursier]
  val default = new SbtCoursierCache

}
