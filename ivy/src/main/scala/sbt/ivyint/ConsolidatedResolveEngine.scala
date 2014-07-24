package sbt
package ivyint

import java.io.File
import collection.concurrent
import org.apache.ivy.core
import core.resolve._
import core.module.id.ModuleRevisionId
import core.report.ResolveReport
import core.module.descriptor.{ DefaultModuleDescriptor, ModuleDescriptor, DependencyDescriptor }
import core.{ IvyPatternHelper, LogOptions }
import org.apache.ivy.util.Message

private[sbt] object ConsolidatedResolveCache {
  def createID(organization: String, name: String, revision: String) =
    ModuleRevisionId.newInstance(organization, name, revision)
  def sbtOrgTemp = "org.scala-sbt.temp"
}

private[sbt] class ConsolidatedResolveCache() {
  import ConsolidatedResolveCache._
  val resolveReportCache: concurrent.Map[ModuleRevisionId, ResolveReport] = concurrent.TrieMap()
  val resolvePropertiesCache: concurrent.Map[ModuleRevisionId, String] = concurrent.TrieMap()
  val directDependencyCache: concurrent.Map[ModuleDescriptor, Vector[DependencyDescriptor]] = concurrent.TrieMap()

  def clean(md0: ModuleDescriptor, prOpt: Option[ProjectResolver]): Unit = {
    val mrid0 = md0.getModuleRevisionId
    val md1 = if (mrid0.getOrganisation == sbtOrgTemp) md0
    else buildConsolidatedModuleDescriptor(md0, prOpt)
    val mrid1 = md1.getModuleRevisionId
    resolveReportCache.remove(mrid1)
    resolvePropertiesCache.remove(mrid1)
  }

  def directDependencies(md0: ModuleDescriptor): Vector[DependencyDescriptor] =
    directDependencyCache.getOrElseUpdate(md0, md0.getDependencies.toVector)

  def buildConsolidatedModuleDescriptor(md0: ModuleDescriptor, prOpt: Option[ProjectResolver]): DefaultModuleDescriptor = {
    def expandInternalDeps(dep: DependencyDescriptor): Vector[DependencyDescriptor] =
      prOpt map {
        _.getModuleDescriptor(dep.getDependencyRevisionId) match {
          case Some(internal) => directDependencies(internal) flatMap expandInternalDeps
          case _              => Vector(dep)
        }
      } getOrElse Vector(dep)
    val expanded = directDependencies(md0) flatMap expandInternalDeps
    val depStrings = expanded map { dep =>
      val mrid = dep.getDependencyRevisionId
      val confMap = (dep.getModuleConfigurations map { conf =>
        conf + "->(" + dep.getDependencyConfigurations(conf).mkString(",") + ")"
      })
      mrid.toString + ";" + confMap.mkString(";")
    }
    val depsString = depStrings.distinct.sorted.mkString("\n")
    val sha1 = Hash.toHex(Hash(depsString))
    // println("sha1: " + sha1)
    val md1 = new DefaultModuleDescriptor(createID(sbtOrgTemp, "temp-resolve-" + sha1, "1.0"), "release", null, false)
    md1
  }
}

private[sbt] trait ConsolidatedResolveEngine extends ResolveEngine {
  import ConsolidatedResolveCache._

  private[sbt] def consolidatedResolveCache: ConsolidatedResolveCache
  private[sbt] def projectResolver: Option[ProjectResolver]

  /**
   * Resolve dependencies of a module described by a module descriptor.
   */
  override def resolve(md0: ModuleDescriptor, options0: ResolveOptions): ResolveReport = {
    val cache = consolidatedResolveCache
    val cacheManager = getSettings.getResolutionCacheManager
    val md1 = cache.buildConsolidatedModuleDescriptor(md0, projectResolver)
    val md1mrid = md1.getModuleRevisionId

    def doWork: (ResolveReport, String) = {
      if (options0.getLog != LogOptions.LOG_QUIET) {
        Message.info("Consolidating managed dependencies to " + md1mrid.toString + " ...")
      }
      md1.setLastModified(System.currentTimeMillis)
      for {
        x <- md0.getConfigurations
      } yield md1.addConfiguration(x)

      for {
        x <- md0.getDependencies
      } yield md1.addDependency(x)

      val options1 = new ResolveOptions(options0)
      options1.setOutputReport(false)
      val report0 = super.resolve(md1, options1)
      val ivyPropertiesInCache1 = cacheManager.getResolvedIvyPropertiesInCache(md1.getResolvedModuleRevisionId)
      val prop0 =
        if (ivyPropertiesInCache1.exists) IO.read(ivyPropertiesInCache1)
        else ""
      if (options0.isOutputReport) {
        this.outputReport(report0, cacheManager, options0)
      }
      cache.resolveReportCache(md1mrid) = report0
      cache.resolvePropertiesCache(md1mrid) = prop0
      (report0, prop0)
    }

    val (report0, prop0) = (cache.resolveReportCache.get(md1mrid), cache.resolvePropertiesCache.get(md1mrid)) match {
      case (Some(report), Some(prop)) =>
        if (options0.getLog != LogOptions.LOG_QUIET) {
          Message.info("Found consolidated dependency " + md1mrid.toString + " ...")
        }
        (report, prop)
      case _ => doWork
    }
    cacheManager.saveResolvedModuleDescriptor(md0)
    if (prop0 != "") {
      val ivyPropertiesInCache0 = cacheManager.getResolvedIvyPropertiesInCache(md0.getResolvedModuleRevisionId)
      IO.write(ivyPropertiesInCache0, prop0)
    }
    report0
  }
}
