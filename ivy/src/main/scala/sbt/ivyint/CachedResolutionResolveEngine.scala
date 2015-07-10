package sbt
package ivyint

import java.util.Date
import java.net.URL
import java.io.File
import java.text.SimpleDateFormat
import collection.concurrent
import collection.mutable
import collection.immutable.ListMap
import org.apache.ivy.Ivy
import org.apache.ivy.core
import core.resolve._
import core.module.id.{ ModuleRevisionId, ModuleId => IvyModuleId }
import core.report.{ ResolveReport, ConfigurationResolveReport, DownloadReport }
import core.module.descriptor.{ DefaultModuleDescriptor, ModuleDescriptor, DefaultDependencyDescriptor, DependencyDescriptor, Configuration => IvyConfiguration, ExcludeRule, IncludeRule }
import core.module.descriptor.{ OverrideDependencyDescriptorMediator, DependencyArtifactDescriptor, DefaultDependencyArtifactDescriptor }
import core.{ IvyPatternHelper, LogOptions }
import org.apache.ivy.util.{ Message, MessageLogger }
import org.apache.ivy.plugins.latest.{ ArtifactInfo => IvyArtifactInfo }
import org.apache.ivy.plugins.matcher.{ MapMatcher, PatternMatcher }
import Configurations.{ System => _, _ }
import annotation.tailrec
import scala.concurrent.duration._

private[sbt] object CachedResolutionResolveCache {
  def createID(organization: String, name: String, revision: String) =
    ModuleRevisionId.newInstance(organization, name, revision)
  def sbtOrgTemp = JsonUtil.sbtOrgTemp
  def graphVersion = "0.13.9B"
  val buildStartup: Long = System.currentTimeMillis
  lazy val todayStr: String = toYyyymmdd(buildStartup)
  lazy val tomorrowStr: String = toYyyymmdd(buildStartup + (1 day).toMillis)
  lazy val yesterdayStr: String = toYyyymmdd(buildStartup - (1 day).toMillis)
  def toYyyymmdd(timeSinceEpoch: Long): String = yyyymmdd.format(new Date(timeSinceEpoch))
  lazy val yyyymmdd: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
}

private[sbt] class CachedResolutionResolveCache() {
  import CachedResolutionResolveCache._
  val updateReportCache: concurrent.Map[ModuleRevisionId, Either[ResolveException, UpdateReport]] = concurrent.TrieMap()
  // Used for subproject
  val projectReportCache: concurrent.Map[(ModuleRevisionId, LogicalClock), Either[ResolveException, UpdateReport]] = concurrent.TrieMap()
  val resolveReportCache: concurrent.Map[ModuleRevisionId, ResolveReport] = concurrent.TrieMap()
  val resolvePropertiesCache: concurrent.Map[ModuleRevisionId, String] = concurrent.TrieMap()
  val conflictCache: concurrent.Map[(ModuleID, ModuleID), (Vector[ModuleID], Vector[ModuleID], String)] = concurrent.TrieMap()
  val maxConflictCacheSize: Int = 1024
  val maxUpdateReportCacheSize: Int = 1024

  def clean(md0: ModuleDescriptor, prOpt: Option[ProjectResolver]): Unit = {
    updateReportCache.clear
  }
  def directDependencies(md0: ModuleDescriptor): Vector[DependencyDescriptor] =
    md0.getDependencies.toVector
  // Returns a vector of (module descriptor, changing, dd)
  def buildArtificialModuleDescriptors(md0: ModuleDescriptor, data: ResolveData, prOpt: Option[ProjectResolver], log: Logger): Vector[(DefaultModuleDescriptor, Boolean, DependencyDescriptor)] =
    {
      log.debug(s":: building artificial module descriptors from ${md0.getModuleRevisionId}")
      // val expanded = expandInternalDependencies(md0, data, prOpt, log)
      val rootModuleConfigs = md0.getConfigurations.toArray.toVector
      directDependencies(md0) map { dd =>
        val arts = dd.getAllDependencyArtifacts.toVector map { x => s"""${x.getName}:${x.getType}:${x.getExt}:${x.getExtraAttributes}""" }
        log.debug(s"::: dd: $dd (artifacts: ${arts.mkString(",")})")
        buildArtificialModuleDescriptor(dd, rootModuleConfigs, md0, prOpt, log)
      }
    }
  def internalDependency(dd: DependencyDescriptor, prOpt: Option[ProjectResolver]): Option[ModuleDescriptor] =
    prOpt match {
      case Some(pr) => pr.getModuleDescriptor(dd.getDependencyRevisionId)
      case _        => None
    }
  def buildArtificialModuleDescriptor(dd: DependencyDescriptor, rootModuleConfigs: Vector[IvyConfiguration],
    parent: ModuleDescriptor, prOpt: Option[ProjectResolver], log: Logger): (DefaultModuleDescriptor, Boolean, DependencyDescriptor) =
    {
      def excludeRuleString(rule: ExcludeRule): String =
        s"""Exclude(${rule.getId},${rule.getConfigurations.mkString(",")},${rule.getMatcher})"""
      def includeRuleString(rule: IncludeRule): String =
        s"""Include(${rule.getId},${rule.getConfigurations.mkString(",")},${rule.getMatcher})"""
      def artifactString(dad: DependencyArtifactDescriptor): String =
        s"""Artifact(${dad.getName},${dad.getType},${dad.getExt},${dad.getUrl},${dad.getConfigurations.mkString(",")},${dad.getExtraAttributes})"""
      val mrid = dd.getDependencyRevisionId
      val confMap = (dd.getModuleConfigurations map { conf =>
        conf + "->(" + dd.getDependencyConfigurations(conf).mkString(",") + ")"
      })
      val exclusions = (dd.getModuleConfigurations.toVector flatMap { conf =>
        dd.getExcludeRules(conf).toVector match {
          case Vector() => None
          case rules    => Some(conf + "->(" + (rules map excludeRuleString).mkString(",") + ")")
        }
      })
      val inclusions = (dd.getModuleConfigurations.toVector flatMap { conf =>
        dd.getIncludeRules(conf).toVector match {
          case Vector() => None
          case rules    => Some(conf + "->(" + (rules map includeRuleString).mkString(",") + ")")
        }
      })
      val explicitArtifacts = dd.getAllDependencyArtifacts.toVector map { artifactString }
      val mes = parent.getAllExcludeRules.toVector
      val mesStr = (mes map excludeRuleString).mkString(",")
      val os = extractOverrides(parent)
      val moduleLevel = s"""dependencyOverrides=${os.mkString(",")};moduleExclusions=$mesStr"""
      val depsString = s"""$mrid;${confMap.mkString(",")};isForce=${dd.isForce};isChanging=${dd.isChanging};isTransitive=${dd.isTransitive};""" +
        s"""exclusions=${exclusions.mkString(",")};inclusions=${inclusions.mkString(",")};explicitArtifacts=${explicitArtifacts.mkString(",")};$moduleLevel;"""
      val sha1 = Hash.toHex(Hash(s"""graphVersion=${CachedResolutionResolveCache.graphVersion};$depsString"""))
      val md1 = new DefaultModuleDescriptor(createID(sbtOrgTemp, "temp-resolve-" + sha1, "1.0"), "release", null, false) with ArtificialModuleDescriptor {
        def targetModuleRevisionId: ModuleRevisionId = mrid
      }
      for {
        conf <- rootModuleConfigs
      } yield md1.addConfiguration(conf)
      md1.addDependency(dd)
      os foreach { ovr =>
        md1.addDependencyDescriptorMediator(ovr.moduleId, ovr.pm, ovr.ddm)
      }
      mes foreach { exclude =>
        md1.addExcludeRule(exclude)
      }
      (md1, IvySbt.isChanging(dd) || internalDependency(dd, prOpt).isDefined, dd)
    }
  def extractOverrides(md0: ModuleDescriptor): Vector[IvyOverride] =
    {
      import scala.collection.JavaConversions._
      (md0.getAllDependencyDescriptorMediators.getAllRules).toSeq.toVector sortBy {
        case (k, v) =>
          k.toString
      } collect {
        case (k: MapMatcher, v: OverrideDependencyDescriptorMediator) =>
          val attr: Map[Any, Any] = k.getAttributes.toMap
          val module = IvyModuleId.newInstance(attr(IvyPatternHelper.ORGANISATION_KEY).toString, attr(IvyPatternHelper.MODULE_KEY).toString)
          val pm = k.getPatternMatcher
          IvyOverride(module, pm, v)
      }
    }
  def getOrElseUpdateMiniGraph(md: ModuleDescriptor, changing0: Boolean, logicalClock: LogicalClock, miniGraphPath: File, cachedDescriptor: File, log: Logger)(f: => Either[ResolveException, UpdateReport]): Either[ResolveException, UpdateReport] =
    {
      import Path._
      val mrid = md.getResolvedModuleRevisionId
      val (pathOrg, pathName, pathRevision) = md match {
        case x: ArtificialModuleDescriptor =>
          val tmrid = x.targetModuleRevisionId
          (tmrid.getOrganisation, tmrid.getName, tmrid.getRevision + "_" + mrid.getName)
        case _ =>
          (mrid.getOrganisation, mrid.getName, mrid.getRevision)
      }
      val staticGraphDirectory = miniGraphPath / "static"
      val dynamicGraphDirectory = miniGraphPath / "dynamic"
      val staticGraphPath = staticGraphDirectory / pathOrg / pathName / pathRevision / "graphs" / "graph.json"
      val dynamicGraphPath = dynamicGraphDirectory / todayStr / logicalClock.toString / pathOrg / pathName / pathRevision / "graphs" / "graph.json"
      def cleanDynamicGraph(): Unit =
        {
          val list = IO.listFiles(dynamicGraphDirectory, DirectoryFilter).toList
          list filterNot { d =>
            (d.getName == todayStr) || (d.getName == tomorrowStr) || (d.getName == yesterdayStr)
          } foreach { d =>
            log.debug(s"deleting old graphs $d...")
            IO.delete(d)
          }
        }
      def loadMiniGraphFromFile: Option[Either[ResolveException, UpdateReport]] =
        (if (staticGraphPath.exists) Some(staticGraphPath)
        else if (dynamicGraphPath.exists) Some(dynamicGraphPath)
        else None) match {
          case Some(path) =>
            log.debug(s"parsing ${path.getAbsolutePath.toString}")
            val ur = JsonUtil.parseUpdateReport(md, path, cachedDescriptor, log)
            if (ur.allFiles forall { _.exists }) {
              updateReportCache(md.getModuleRevisionId) = Right(ur)
              Some(Right(ur))
            } else {
              log.debug(s"some files are missing from the cache, so invalidating the minigraph")
              IO.delete(path)
              None
            }
          case _ => None
        }
      (updateReportCache.get(mrid) orElse loadMiniGraphFromFile) match {
        case Some(result) =>
          result match {
            case Right(ur) => Right(ur.withStats(ur.stats.withCached(true)))
            case x         => x
          }
        case None =>
          f match {
            case Right(ur) =>
              val changing = changing0 || (ur.configurations exists { cr =>
                cr.details exists { oar =>
                  oar.modules exists { mr =>
                    IvySbt.isChanging(mr.module) || (mr.callers exists { _.isChangingDependency })
                  }
                }
              })
              IO.createDirectory(miniGraphPath)
              val gp = if (changing) dynamicGraphPath
              else staticGraphPath
              log.debug(s"saving minigraph to $gp")
              if (changing) {
                cleanDynamicGraph()
              }
              JsonUtil.writeUpdateReport(ur, gp)
              // limit the update cache size
              if (updateReportCache.size > maxUpdateReportCacheSize) {
                updateReportCache.remove(updateReportCache.head._1)
              }
              // don't cache dynamic graphs in memory.
              if (!changing) {
                updateReportCache(md.getModuleRevisionId) = Right(ur)
              }
              Right(ur)
            case Left(re) =>
              if (!changing0) {
                updateReportCache(md.getModuleRevisionId) = Left(re)
              }
              Left(re)
          }
      }
    }

  def getOrElseUpdateConflict(cf0: ModuleID, cf1: ModuleID, conflicts: Vector[ModuleReport])(f: => (Vector[ModuleReport], Vector[ModuleReport], String)): (Vector[ModuleReport], Vector[ModuleReport]) =
    {
      def reconstructReports(surviving: Vector[ModuleID], evicted: Vector[ModuleID], mgr: String): (Vector[ModuleReport], Vector[ModuleReport]) = {
        val moduleIdMap = Map(conflicts map { x => x.module -> x }: _*)
        (surviving map moduleIdMap, evicted map moduleIdMap map { _.copy(evicted = true, evictedReason = Some(mgr.toString)) })
      }
      (conflictCache get ((cf0, cf1))) match {
        case Some((surviving, evicted, mgr)) => reconstructReports(surviving, evicted, mgr)
        case _ =>
          (conflictCache get ((cf1, cf0))) match {
            case Some((surviving, evicted, mgr)) => reconstructReports(surviving, evicted, mgr)
            case _ =>
              val (surviving, evicted, mgr) = f
              if (conflictCache.size > maxConflictCacheSize) {
                conflictCache.remove(conflictCache.head._1)
              }
              conflictCache((cf0, cf1)) = (surviving map { _.module }, evicted map { _.module }, mgr)
              (surviving, evicted)
          }
      }
    }
  def getOrElseUpdateProjectReport(mrid: ModuleRevisionId, logicalClock: LogicalClock)(f: => Either[ResolveException, UpdateReport]): Either[ResolveException, UpdateReport] =
    if (projectReportCache contains (mrid -> logicalClock)) projectReportCache((mrid, logicalClock))
    else {
      val oldKeys = projectReportCache.keys filter { case (x, clk) => clk != logicalClock }
      projectReportCache --= oldKeys
      projectReportCache.getOrElseUpdate((mrid, logicalClock), f)
    }
}

private[sbt] trait ArtificialModuleDescriptor { self: DefaultModuleDescriptor =>
  def targetModuleRevisionId: ModuleRevisionId
}

private[sbt] trait CachedResolutionResolveEngine extends ResolveEngine {
  import CachedResolutionResolveCache._

  private[sbt] def cachedResolutionResolveCache: CachedResolutionResolveCache
  private[sbt] def projectResolver: Option[ProjectResolver]
  private[sbt] def makeInstance: Ivy
  private[sbt] val ignoreTransitiveForce: Boolean = true

  def withIvy[A](log: Logger)(f: Ivy => A): A =
    withIvy(new IvyLoggerInterface(log))(f)
  def withIvy[A](log: MessageLogger)(f: Ivy => A): A =
    withDefaultLogger(log) {
      val ivy = makeInstance
      ivy.pushContext()
      ivy.getLoggerEngine.pushLogger(log)
      try { f(ivy) }
      finally {
        ivy.getLoggerEngine.popLogger()
        ivy.popContext()
      }
    }
  def withDefaultLogger[A](log: MessageLogger)(f: => A): A =
    {
      val originalLogger = Message.getDefaultLogger
      Message.setDefaultLogger(log)
      try { f }
      finally { Message.setDefaultLogger(originalLogger) }
    }

  /**
   * This returns sbt's UpdateReport structure.
   * missingOk allows sbt to call this with classifiers that may or may not exist, and grab the JARs.
   */
  def customResolve(md0: ModuleDescriptor, missingOk: Boolean, logicalClock: LogicalClock, options0: ResolveOptions, depDir: File, log: Logger): Either[ResolveException, UpdateReport] =
    cachedResolutionResolveCache.getOrElseUpdateProjectReport(md0.getModuleRevisionId, logicalClock) {
      import Path._
      val start = System.currentTimeMillis
      val miniGraphPath = depDir / "module"
      val cachedDescriptor = getSettings.getResolutionCacheManager.getResolvedIvyFileInCache(md0.getModuleRevisionId)
      val cache = cachedResolutionResolveCache
      val os = cache.extractOverrides(md0)
      val options1 = new ResolveOptions(options0)
      val data = new ResolveData(this, options1)
      val mds = cache.buildArtificialModuleDescriptors(md0, data, projectResolver, log)

      def doWork(md: ModuleDescriptor, dd: DependencyDescriptor): Either[ResolveException, UpdateReport] =
        cache.internalDependency(dd, projectResolver) match {
          case Some(md1) =>
            log.debug(s":: call customResolve recursively: $dd")
            customResolve(md1, missingOk, logicalClock, options0, depDir, log) match {
              case Right(ur) => Right(remapInternalProject(new IvyNode(data, md1), ur, md0, dd, os, log))
              case Left(e)   => Left(e)
            }
          case None =>
            log.debug(s":: call ivy resolution: $dd")
            doWorkUsingIvy(md)
        }
      def doWorkUsingIvy(md: ModuleDescriptor): Either[ResolveException, UpdateReport] =
        {
          val options1 = new ResolveOptions(options0)
          var rr = withIvy(log) { ivy =>
            ivy.resolve(md, options1)
          }
          if (!rr.hasError || missingOk) Right(IvyRetrieve.updateReport(rr, cachedDescriptor))
          else {
            val messages = rr.getAllProblemMessages.toArray.map(_.toString).distinct
            val failedPaths = ListMap(rr.getUnresolvedDependencies map { node =>
              val m = IvyRetrieve.toModuleID(node.getId)
              val path = IvyRetrieve.findPath(node, md.getModuleRevisionId) map { x =>
                IvyRetrieve.toModuleID(x.getId)
              }
              log.debug("- Unresolved path " + path.toString)
              m -> path
            }: _*)
            val failed = failedPaths.keys.toSeq
            Left(new ResolveException(messages, failed, failedPaths))
          }
        }
      val results = mds map {
        case (md, changing, dd) =>
          cache.getOrElseUpdateMiniGraph(md, changing, logicalClock, miniGraphPath, cachedDescriptor, log) {
            doWork(md, dd)
          }
      }
      val uReport = mergeResults(md0, results, missingOk, System.currentTimeMillis - start, os, log)
      val cacheManager = getSettings.getResolutionCacheManager
      cacheManager.saveResolvedModuleDescriptor(md0)
      val prop0 = ""
      val ivyPropertiesInCache0 = cacheManager.getResolvedIvyPropertiesInCache(md0.getResolvedModuleRevisionId)
      IO.write(ivyPropertiesInCache0, prop0)
      uReport
    }

  def mergeResults(md0: ModuleDescriptor, results: Vector[Either[ResolveException, UpdateReport]], missingOk: Boolean, resolveTime: Long,
    os: Vector[IvyOverride], log: Logger): Either[ResolveException, UpdateReport] =
    if (!missingOk && (results exists { _.isLeft })) Left(mergeErrors(md0, results collect { case Left(re) => re }, log))
    else Right(mergeReports(md0, results collect { case Right(ur) => ur }, resolveTime, os, log))
  def mergeErrors(md0: ModuleDescriptor, errors: Vector[ResolveException], log: Logger): ResolveException =
    {
      val messages = errors flatMap { _.messages }
      val failed = errors flatMap { _.failed }
      val failedPaths = errors flatMap {
        _.failedPaths.toList map {
          case (failed, paths) =>
            if (paths.isEmpty) (failed, paths)
            else (failed, List(IvyRetrieve.toModuleID(md0.getResolvedModuleRevisionId)) ::: paths.toList.tail)
        }
      }
      new ResolveException(messages, failed, ListMap(failedPaths: _*))
    }
  def mergeReports(md0: ModuleDescriptor, reports: Vector[UpdateReport], resolveTime: Long, os: Vector[IvyOverride], log: Logger): UpdateReport =
    {
      log.debug(s":: merging update reports")
      val cachedDescriptor = getSettings.getResolutionCacheManager.getResolvedIvyFileInCache(md0.getModuleRevisionId)
      val rootModuleConfigs = md0.getConfigurations.toVector
      val cachedReports = reports filter { !_.stats.cached }
      val stats = new UpdateStats(resolveTime, (cachedReports map { _.stats.downloadTime }).sum, (cachedReports map { _.stats.downloadSize }).sum, false)
      val configReports = rootModuleConfigs map { conf =>
        log.debug("::: -----------")
        val crs = reports flatMap { _.configurations filter { _.configuration == conf.getName } }
        mergeConfigurationReports(conf.getName, crs, os, log)
      }
      new UpdateReport(cachedDescriptor, configReports, stats, Map.empty)
    }
  // memory usage 62%, of which 58% is in mergeOrganizationArtifactReports
  def mergeConfigurationReports(rootModuleConf: String, reports: Vector[ConfigurationReport], os: Vector[IvyOverride], log: Logger): ConfigurationReport =
    {
      // get the details right, and the rest could be derived
      val details = mergeOrganizationArtifactReports(rootModuleConf, reports flatMap { _.details }, os, log)
      val modules = details flatMap {
        _.modules filter { mr =>
          !mr.evicted && mr.problem.isEmpty
        }
      }
      val evicted = details flatMap {
        _.modules filter { mr =>
          mr.evicted
        }
      } map { _.module }
      new ConfigurationReport(rootModuleConf, modules, details, evicted)
    }
  /**
   * Returns a tuple of (merged org + name combo, newly evicted modules)
   */
  def mergeOrganizationArtifactReports(rootModuleConf: String, reports0: Vector[OrganizationArtifactReport], os: Vector[IvyOverride], log: Logger): Vector[OrganizationArtifactReport] =
    {
      // group by takes up too much memory. trading space with time.
      val orgNamePairs = (reports0 map { oar => (oar.organization, oar.name) }).distinct
      // this might take up some memory, but it's limited to a single
      val reports1 = reports0 map { filterOutCallers }
      val allModules: ListMap[(String, String), Vector[OrganizationArtifactReport]] =
        ListMap(orgNamePairs map {
          case (organization, name) =>
            val xs = reports1 filter { oar => oar.organization == organization && oar.name == name }
            ((organization, name), xs)
        }: _*)
      val stackGuard = reports0.size * reports0.size * 2
      // sort the all modules such that less called modules comes earlier
      def sortModules(cs: ListMap[(String, String), Vector[OrganizationArtifactReport]],
        n: Int): ListMap[(String, String), Vector[OrganizationArtifactReport]] =
        {
          val keys = cs.keySet
          val (called, notCalled) = cs partition {
            case (k, oas) =>
              oas exists {
                _.modules.exists {
                  _.callers exists { caller =>
                    val m = caller.caller
                    keys((m.organization, m.name))
                  }
                }
              }
          }
          notCalled ++
            (if (called.isEmpty || n > stackGuard) called
            else sortModules(called, n + 1))
        }
      def resolveConflicts(cs: List[((String, String), Vector[OrganizationArtifactReport])]): List[OrganizationArtifactReport] =
        cs match {
          case Nil => Nil
          case (k, Vector()) :: rest => resolveConflicts(rest)
          case (k, Vector(oa)) :: rest if (oa.modules.size == 0) => resolveConflicts(rest)
          case (k, Vector(oa)) :: rest if (oa.modules.size == 1 && !oa.modules.head.evicted) =>
            log.debug(s":: no conflict $rootModuleConf: ${oa.organization}:${oa.name}")
            oa :: resolveConflicts(rest)
          case ((organization, name), oas) :: rest =>
            (mergeModuleReports(rootModuleConf, oas flatMap { _.modules }, os, log) match {
              case (survivor, newlyEvicted) =>
                val evicted = (survivor ++ newlyEvicted) filter { m => m.evicted }
                val notEvicted = (survivor ++ newlyEvicted) filter { m => !m.evicted }
                log.debug("::: adds " + (notEvicted map { _.module }).mkString(", "))
                log.debug("::: evicted " + (evicted map { _.module }).mkString(", "))
                val x = new OrganizationArtifactReport(organization, name, survivor ++ newlyEvicted)
                val next = transitivelyEvict(rootModuleConf, rest, evicted, log)
                x :: resolveConflicts(next)
            })
        }
      val sorted = sortModules(allModules, 0)
      val result = resolveConflicts(sorted.toList)
      result.toVector
    }
  def filterOutCallers(report0: OrganizationArtifactReport): OrganizationArtifactReport =
    OrganizationArtifactReport(
      report0.organization,
      report0.name,
      report0.modules map { mr =>
        // https://github.com/sbt/sbt/issues/1763
        mr.copy(callers = JsonUtil.filterOutArtificialCallers(mr.callers))
      })
  /**
   * Merges ModuleReports, which represents orgnization, name, and version.
   * Returns a touple of (surviving modules ++ non-conflicting modules, newly evicted modules).
   */
  def mergeModuleReports(rootModuleConf: String, modules: Seq[ModuleReport], os: Vector[IvyOverride], log: Logger): (Vector[ModuleReport], Vector[ModuleReport]) =
    {
      if (modules.nonEmpty) {
        log.debug(s":: merging module reports for $rootModuleConf: ${modules.head.module.organization}:${modules.head.module.name}")
      }
      def mergeModuleReports(org: String, name: String, version: String, xs: Seq[ModuleReport]): ModuleReport = {
        val completelyEvicted = xs forall { _.evicted }
        val allCallers = xs flatMap { _.callers }
        val allArtifacts = (xs flatMap { _.artifacts }).distinct
        xs.head.copy(artifacts = allArtifacts, evicted = completelyEvicted, callers = allCallers)
      }
      val merged = (modules groupBy { m => (m.module.organization, m.module.name, m.module.revision) }).toSeq.toVector flatMap {
        case ((org, name, version), xs) =>
          if (xs.size < 2) xs
          else Vector(mergeModuleReports(org, name, version, xs))
      }
      val conflicts = merged filter { m => !m.evicted && m.problem.isEmpty }
      if (conflicts.size < 2) (merged, Vector())
      else resolveConflict(rootModuleConf, conflicts, os, log) match {
        case (survivor, evicted) =>
          (survivor ++ (merged filter { m => m.evicted || m.problem.isDefined }), evicted)
      }
    }
  /**
   * This transitively evicts any non-evicted modules whose only callers are newly evicted.
   */
  def transitivelyEvict(rootModuleConf: String, reports0: List[((String, String), Vector[OrganizationArtifactReport])],
    evicted0: Vector[ModuleReport], log: Logger): List[((String, String), Vector[OrganizationArtifactReport])] =
    {
      val em = (evicted0 map { _.module }).toSet
      def isTransitivelyEvicted(mr: ModuleReport): Boolean =
        mr.callers forall { c => em(c.caller) }
      val reports: List[((String, String), Vector[OrganizationArtifactReport])] = reports0 map {
        case ((organization, name), oars0) =>
          val oars = oars0 map { oar =>
            val (affected, unaffected) = oar.modules partition { mr =>
              val x = !mr.evicted && mr.problem.isEmpty && isTransitivelyEvicted(mr)
              if (x) {
                log.debug(s""":::: transitively evicted $rootModuleConf: ${mr.module}""")
              }
              x
            }
            val newlyEvicted = affected map { _.copy(evicted = true, evictedReason = Some("transitive-evict")) }
            if (affected.isEmpty) oar
            else new OrganizationArtifactReport(organization, name, unaffected ++ newlyEvicted)
          }
          ((organization, name), oars)
      }
      reports
    }
  /**
   * resolves dependency resolution conflicts in which multiple candidates are found for organization+name combos.
   * The main input is conflicts, which is a Vector of ModuleReport, which contains full info on the modulerevision, including its callers.
   * Conflict resolution could be expensive, so this is first cached to `cachedResolutionResolveCache` if the conflict is between 2 modules.
   * Otherwise, the default "latest" resolution takes the following precedence:
   *   1. overrides passed in to `os`.
   *   2. diretly forced dependency within the artificial module.
   *   3. latest revision.
   * Note transitively forced dependencies are not respected. This seems to be the case for stock Ivy's behavior as well,
   * which may be because Ivy makes all Maven dependencies as forced="true".
   */
  def resolveConflict(rootModuleConf: String, conflicts: Vector[ModuleReport], os: Vector[IvyOverride], log: Logger): (Vector[ModuleReport], Vector[ModuleReport]) =
    {
      import org.apache.ivy.plugins.conflict.{ NoConflictManager, StrictConflictManager, LatestConflictManager }
      val head = conflicts.head
      val organization = head.module.organization
      val name = head.module.name
      log.debug(s"::: resolving conflict in $rootModuleConf:$organization:$name " + (conflicts map { _.module }).mkString("(", ", ", ")"))
      def useLatest(lcm: LatestConflictManager): (Vector[ModuleReport], Vector[ModuleReport], String) =
        (conflicts find { m =>
          m.callers.exists { _.isDirectlyForceDependency }
        }) match {
          case Some(m) =>
            log.debug(s"- directly forced dependency: $m ${m.callers}")
            (Vector(m), conflicts filterNot { _ == m } map { _.copy(evicted = true, evictedReason = Some("direct-force")) }, "direct-force")
          case None =>
            (conflicts find { m =>
              m.callers.exists { _.isForceDependency }
            }) match {
              // Ivy translates pom.xml dependencies to forced="true", so transitive force is broken.
              case Some(m) if !ignoreTransitiveForce =>
                log.debug(s"- transitively forced dependency: $m ${m.callers}")
                (Vector(m), conflicts filterNot { _ == m } map { _.copy(evicted = true, evictedReason = Some("transitive-force")) }, "transitive-force")
              case _ =>
                val strategy = lcm.getStrategy
                val infos = conflicts map { ModuleReportArtifactInfo(_) }
                Option(strategy.findLatest(infos.toArray, None.orNull)) match {
                  case Some(ModuleReportArtifactInfo(m)) =>
                    (Vector(m), conflicts filterNot { _ == m } map { _.copy(evicted = true, evictedReason = Some(lcm.toString)) }, lcm.toString)
                  case _ => (conflicts, Vector(), lcm.toString)
                }
            }
        }
      def doResolveConflict: (Vector[ModuleReport], Vector[ModuleReport], String) =
        os find { ovr => ovr.moduleId.getOrganisation == organization && ovr.moduleId.getName == name } match {
          case Some(ovr) if Option(ovr.ddm.getVersion).isDefined =>
            val ovrVersion = ovr.ddm.getVersion
            conflicts find { mr =>
              mr.module.revision == ovrVersion
            } match {
              case Some(m) =>
                (Vector(m), conflicts filterNot { _ == m } map { _.copy(evicted = true, evictedReason = Some("override")) }, "override")
              case None =>
                sys.error(s"override dependency specifies $ovrVersion but no candidates were found: " + (conflicts map { _.module }).mkString("(", ", ", ")"))
            }
          case None =>
            getSettings.getConflictManager(IvyModuleId.newInstance(organization, name)) match {
              case ncm: NoConflictManager     => (conflicts, Vector(), ncm.toString)
              case _: StrictConflictManager   => sys.error((s"conflict was found in $rootModuleConf:$organization:$name " + (conflicts map { _.module }).mkString("(", ", ", ")")))
              case lcm: LatestConflictManager => useLatest(lcm)
              case conflictManager            => sys.error(s"Unsupported conflict manager $conflictManager")
            }
        }
      if (conflicts.size == 2 && os.isEmpty) {
        val (cf0, cf1) = (conflicts(0).module, conflicts(1).module)
        val cache = cachedResolutionResolveCache
        val (surviving, evicted) = cache.getOrElseUpdateConflict(cf0, cf1, conflicts) { doResolveConflict }
        (surviving, evicted)
      } else {
        val (surviving, evicted, mgr) = doResolveConflict
        (surviving, evicted)
      }
    }
  def remapInternalProject(node: IvyNode, ur: UpdateReport,
    md0: ModuleDescriptor, dd: DependencyDescriptor,
    os: Vector[IvyOverride], log: Logger): UpdateReport =
    {
      def parentConfigs(c: String): Vector[String] =
        Option(md0.getConfiguration(c)) match {
          case Some(config) =>
            config.getExtends.toVector ++
              (config.getExtends.toVector flatMap parentConfigs)
          case None => Vector()
        }
      // These are the configurations from the original project we want to resolve.
      val rootModuleConfs = md0.getConfigurations.toArray.toVector
      val configurations0 = ur.configurations.toVector
      // This is how md looks from md0 via dd's mapping.
      val remappedConfigs0: Map[String, Vector[String]] = Map(rootModuleConfs map { conf0 =>
        val remapped: Vector[String] = dd.getDependencyConfigurations(conf0.getName).toVector flatMap { conf =>
          node.getRealConfs(conf).toVector
        }
        conf0.getName -> remapped
      }: _*)
      // This emulates test-internal extending test configuration etc.
      val remappedConfigs: Map[String, Vector[String]] =
        (remappedConfigs0 /: rootModuleConfs) { (acc0, c) =>
          val ps = parentConfigs(c.getName)
          (acc0 /: ps) { (acc, parent) =>
            val vs0 = acc.getOrElse(c.getName, Vector())
            val vs = acc.getOrElse(parent, Vector())
            acc.updated(c.getName, (vs0 ++ vs).distinct)
          }
        }
      log.debug(s"::: remapped configs $remappedConfigs")
      val configurations = rootModuleConfs map { conf0 =>
        val remappedCRs = configurations0 filter { cr =>
          remappedConfigs(conf0.getName) contains cr.configuration
        }
        mergeConfigurationReports(conf0.getName, remappedCRs, os, log)
      }
      new UpdateReport(ur.cachedDescriptor, configurations, ur.stats, ur.stamps)
    }
}

private[sbt] case class ModuleReportArtifactInfo(moduleReport: ModuleReport) extends IvyArtifactInfo {
  override def getLastModified: Long = moduleReport.publicationDate map { _.getTime } getOrElse 0L
  override def getRevision: String = moduleReport.module.revision
}
private[sbt] case class IvyOverride(moduleId: IvyModuleId, pm: PatternMatcher, ddm: OverrideDependencyDescriptorMediator) {
  override def toString: String = s"""IvyOverride($moduleId,$pm,${ddm.getVersion},${ddm.getBranch})"""
}
