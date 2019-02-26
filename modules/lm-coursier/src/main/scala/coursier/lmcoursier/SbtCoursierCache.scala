package coursier.lmcoursier

import java.util.concurrent.ConcurrentHashMap

import coursier.core._
import sbt.librarymanagement.UpdateReport

class SbtCoursierCache {

  import SbtCoursierCache._

  private val resolutionsCache = new ConcurrentHashMap[ResolutionKey, Map[Set[Configuration], Resolution]]
  // these may actually not need to be cached any more, now that the resolutions
  // are cached
  private val reportsCache = new ConcurrentHashMap[ReportKey, UpdateReport]


  def resolutionOpt(key: ResolutionKey): Option[Map[Set[Configuration], Resolution]] =
    Option(resolutionsCache.get(key))
  def putResolution(key: ResolutionKey, res: Map[Set[Configuration], Resolution]): Unit =
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

object SbtCoursierCache {

  final case class ResolutionKey(
    dependencies: Seq[(Configuration, Dependency)],
    repositories: Seq[Repository],
    params: ResolutionParams,
    sbtClassifiers: Boolean
  )

  final case class ReportKey(
    dependencies: Seq[(Configuration, Dependency)],
    resolution: Map[Set[Configuration], Resolution],
    withClassifiers: Boolean,
    sbtClassifiers: Boolean
  )


  private[coursier] val default = new SbtCoursierCache

}
