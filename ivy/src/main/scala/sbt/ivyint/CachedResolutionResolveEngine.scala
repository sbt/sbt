package sbt
package ivyint

import java.util.Date
import java.net.URL
import java.io.File
import collection.concurrent
import collection.immutable.ListMap
import org.apache.ivy.Ivy
import org.apache.ivy.core
import core.resolve._
import core.module.id.{ ModuleRevisionId, ModuleId => IvyModuleId }
import core.report.{ ResolveReport, ConfigurationResolveReport, DownloadReport }
import core.module.descriptor.{ DefaultModuleDescriptor, ModuleDescriptor, DefaultDependencyDescriptor, DependencyDescriptor, Configuration => IvyConfiguration, ExcludeRule, IncludeRule }
import core.module.descriptor.{ OverrideDependencyDescriptorMediator, DependencyArtifactDescriptor, DefaultDependencyArtifactDescriptor }
import core.{ IvyPatternHelper, LogOptions }
import org.apache.ivy.util.Message
import org.apache.ivy.plugins.latest.{ ArtifactInfo => IvyArtifactInfo }
import org.apache.ivy.plugins.matcher.{ MapMatcher, PatternMatcher }

private[sbt] object CachedResolutionResolveCache {
  def createID(organization: String, name: String, revision: String) =
    ModuleRevisionId.newInstance(organization, name, revision)
  def sbtOrgTemp = "org.scala-sbt.temp"
}

private[sbt] class CachedResolutionResolveCache() {
  import CachedResolutionResolveCache._
  val updateReportCache: concurrent.Map[ModuleRevisionId, Either[ResolveException, UpdateReport]] = concurrent.TrieMap()
  val resolveReportCache: concurrent.Map[ModuleRevisionId, ResolveReport] = concurrent.TrieMap()
  val resolvePropertiesCache: concurrent.Map[ModuleRevisionId, String] = concurrent.TrieMap()
  val conflictCache: concurrent.Map[(ModuleID, ModuleID), (Vector[ModuleID], Vector[ModuleID], String)] = concurrent.TrieMap()
  val maxConflictCacheSize: Int = 10000

  def clean(md0: ModuleDescriptor, prOpt: Option[ProjectResolver]): Unit = {
    updateReportCache.clear
  }
  def directDependencies(md0: ModuleDescriptor): Vector[DependencyDescriptor] =
    md0.getDependencies.toVector
  def buildArtificialModuleDescriptors(md0: ModuleDescriptor, data: ResolveData, prOpt: Option[ProjectResolver], log: Logger): Vector[(DefaultModuleDescriptor, Boolean)] =
    {
      log.debug(s":: building artificial module descriptors from ${md0.getModuleRevisionId}")
      val expanded = expandInternalDependencies(md0, data, prOpt, log)
      val rootModuleConfigs = md0.getConfigurations.toArray.toVector
      expanded map { dd =>
        val arts = dd.getAllDependencyArtifacts.toVector map { x => s"""${x.getName}:${x.getType}:${x.getExt}:${x.getExtraAttributes}""" }
        log.debug(s"::: expanded dd: $dd (artifacts: ${arts.mkString(",")})")
        buildArtificialModuleDescriptor(dd, rootModuleConfigs, md0, prOpt, log)
      }
    }
  // This expands out all internal dependencies and merge them into a single graph that consists
  // only of external dependencies.
  // The tricky part is the merger of configurations, even though in most cases we will only see compile->compile when it comes to internal deps.
  // Theoretically, there could be a potential for test->test->runtime kind of situation. nextConfMap and remapConfigurations track
  // the configuration chains transitively.
  def expandInternalDependencies(md0: ModuleDescriptor, data: ResolveData, prOpt: Option[ProjectResolver], log: Logger): Vector[DependencyDescriptor] =
    {
      log.debug(s"::: expanding internal dependencies of module descriptor ${md0.getModuleRevisionId}")
      val rootModuleConfigs = md0.getConfigurations.toArray.toVector
      val rootNode = new IvyNode(data, md0)
      def expandInternalDeps(dep: DependencyDescriptor, confMap: Map[String, Array[String]]): Vector[DependencyDescriptor] =
        internalDependency(dep) match {
          case Some(internal) =>
            log.debug(s""":::: found internal dependency ${internal.getResolvedModuleRevisionId}""")
            val allConfigurations: Vector[String] = confMap.values.flatten.toVector.distinct
            val next = nextConfMap(dep, confMap)
            // direct dependencies of an internal dependency
            val directs0 = directDependencies(internal)
            val directs = directs0 filter { dd =>
              allConfigurations exists { conf => !dd.getDependencyConfigurations(conf).isEmpty }
            }
            directs flatMap { dd => expandInternalDeps(dd, next) }
          case _ =>
            Vector(remapConfigurations(dep, confMap, log))
        }
      def internalDependency(dep: DependencyDescriptor): Option[ModuleDescriptor] =
        prOpt match {
          case Some(pr) => pr.getModuleDescriptor(dep.getDependencyRevisionId)
          case _        => None
        }
      // This creates confMap. The key of the map is rootModuleConf for md0, the value is the dependency configs for dd.
      def nextConfMap(dd: DependencyDescriptor, previous: Map[String, Array[String]]): Map[String, Array[String]] =
        previous map {
          case (rootModuleConf, vs) =>
            rootModuleConf -> (vs flatMap { conf =>
              dd.getDependencyConfigurations(conf) flatMap { confName =>
                if (confName == "*") Array(confName)
                else rootNode.getRealConfs(confName)
              }
            })
        }
      // The key of the confMap is rootModuleConf for md0, and the values are modules configuratons of dd0.
      // For example if project Root depends on project B % "test", and project B depends on junit,
      // confMap should contain Map("test", Array("compile")).
      // This remaps junit dependency as junit % "test". 
      def remapConfigurations(dd0: DependencyDescriptor, confMap: Map[String, Array[String]], log: Logger): DependencyDescriptor =
        {
          log.debug(s""":::: remapping configuration of ${dd0} with ${confMap.toList map { case (k, v) => (k, v.toList) }}""")
          val dd = new DefaultDependencyDescriptor(md0, dd0.getDependencyRevisionId, dd0.getDynamicConstraintDependencyRevisionId,
            dd0.isForce, dd0.isChanging, dd0.isTransitive)
          val moduleConfigurations = dd0.getModuleConfigurations.toVector
          for {
            moduleConf <- moduleConfigurations
            (rootModuleConf, vs) <- confMap.toSeq
          } if (vs contains moduleConf) {
            log.debug(s""":::: ${dd0}: $moduleConf maps to $rootModuleConf""")
            dd0.getDependencyConfigurations(moduleConf) foreach { conf =>
              dd.addDependencyConfiguration(rootModuleConf, conf)
            }
            dd0.getIncludeRules(moduleConf) foreach { rule =>
              dd.addIncludeRule(rootModuleConf, rule)
            }
            dd0.getExcludeRules(moduleConf) foreach { rule =>
              dd.addExcludeRule(rootModuleConf, rule)
            }
            dd0.getAllDependencyArtifacts foreach { dad0 =>
              (Option(dad0.getConfigurations) map { confs => confs.isEmpty || confs.contains(moduleConf) || confs.contains("*") }) match {
                case Some(false) => // do nothing
                case _ =>
                  val dad = new DefaultDependencyArtifactDescriptor(dd, dad0.getName, dad0.getType, dad0.getExt, dad0.getUrl, dad0.getExtraAttributes)
                  dad.addConfiguration(rootModuleConf)
                  dd.addDependencyArtifact(rootModuleConf, dad)
              }
            }
          }
          log.debug(s""":::: remapped dd: $dd""")
          dd
        }
      directDependencies(md0) flatMap { dep =>
        val initialMap = Map(dep.getModuleConfigurations map { rootModuleConf =>
          (rootModuleConf -> Array(rootModuleConf))
        }: _*)
        expandInternalDeps(dep, initialMap)
      }
    }
  def buildArtificialModuleDescriptor(dd: DependencyDescriptor, rootModuleConfigs: Vector[IvyConfiguration],
    parent: ModuleDescriptor, prOpt: Option[ProjectResolver], log: Logger): (DefaultModuleDescriptor, Boolean) =
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
      val sha1 = Hash.toHex(Hash(depsString))
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
      (md1, IvySbt.isChanging(dd))
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
      val dynamicGraphPath = dynamicGraphDirectory / logicalClock.toString / pathOrg / pathName / pathRevision / "graphs" / "graph.json"
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
              JsonUtil.writeUpdateReport(ur, gp)
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

  /**
   * This returns sbt's UpdateReport structure.
   * missingOk allows sbt to call this with classifiers that may or may not exist, and grab the JARs.
   */
  def customResolve(md0: ModuleDescriptor, missingOk: Boolean, logicalClock: LogicalClock, options0: ResolveOptions, depDir: File, log: Logger): Either[ResolveException, UpdateReport] = {
    import Path._
    val start = System.currentTimeMillis
    val miniGraphPath = depDir / "module"
    val cachedDescriptor = getSettings.getResolutionCacheManager.getResolvedIvyFileInCache(md0.getModuleRevisionId)
    val cache = cachedResolutionResolveCache
    val os = cache.extractOverrides(md0)
    val options1 = new ResolveOptions(options0)
    val data = new ResolveData(this, options1)
    val mds = cache.buildArtificialModuleDescriptors(md0, data, projectResolver, log)
    def doWork(md: ModuleDescriptor): Either[ResolveException, UpdateReport] =
      {
        val options1 = new ResolveOptions(options0)
        val i = makeInstance
        var rr = i.resolve(md, options1)
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
      case (md, changing) =>
        cache.getOrElseUpdateMiniGraph(md, changing, logicalClock, miniGraphPath, cachedDescriptor, log) {
          doWork(md)
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
      val cachedDescriptor = getSettings.getResolutionCacheManager.getResolvedIvyFileInCache(md0.getModuleRevisionId)
      val rootModuleConfigs = md0.getConfigurations.toVector
      val cachedReports = reports filter { !_.stats.cached }
      val stats = new UpdateStats(resolveTime, (cachedReports map { _.stats.downloadTime }).sum, (cachedReports map { _.stats.downloadSize }).sum, false)
      val configReports = rootModuleConfigs map { conf =>
        val crs = reports flatMap { _.configurations filter { _.configuration == conf.getName } }
        mergeConfigurationReports(conf.getName, crs, os, log)
      }
      new UpdateReport(cachedDescriptor, configReports, stats, Map.empty)
    }
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
  def mergeOrganizationArtifactReports(rootModuleConf: String, reports0: Vector[OrganizationArtifactReport], os: Vector[IvyOverride], log: Logger): Vector[OrganizationArtifactReport] =
    (reports0 groupBy { oar => (oar.organization, oar.name) }).toSeq.toVector flatMap {
      case ((org, name), xs) =>
        if (xs.size < 2) xs
        else Vector(new OrganizationArtifactReport(org, name, mergeModuleReports(rootModuleConf, xs flatMap { _.modules }, os, log)))
    }
  def mergeModuleReports(rootModuleConf: String, modules: Vector[ModuleReport], os: Vector[IvyOverride], log: Logger): Vector[ModuleReport] =
    {
      def mergeModuleReports(org: String, name: String, version: String, xs: Vector[ModuleReport]): ModuleReport = {
        val completelyEvicted = xs forall { _.evicted }
        val allCallers = xs flatMap { _.callers }
        val allArtifacts = (xs flatMap { _.artifacts }).distinct
        log.debug(s":: merging module report for $org:$name:$version - $allArtifacts")
        xs.head.copy(artifacts = allArtifacts, evicted = completelyEvicted, callers = allCallers)
      }
      val merged = (modules groupBy { m => (m.module.organization, m.module.name, m.module.revision) }).toSeq.toVector flatMap {
        case ((org, name, version), xs) =>
          if (xs.size < 2) xs
          else Vector(mergeModuleReports(org, name, version, xs))
      }
      val conflicts = merged filter { m => !m.evicted && m.problem.isEmpty }
      if (conflicts.size < 2) merged
      else resolveConflict(rootModuleConf, conflicts, os, log) match {
        case (survivor, evicted) =>
          survivor ++ evicted ++ (merged filter { m => m.evicted || m.problem.isDefined })
      }
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
      log.debug(s"- conflict in $rootModuleConf:$organization:$name " + (conflicts map { _.module }).mkString("(", ", ", ")"))
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
        cache.getOrElseUpdateConflict(cf0, cf1, conflicts) { doResolveConflict }
      } else {
        val (surviving, evicted, mgr) = doResolveConflict
        (surviving, evicted)
      }
    }
}

private[sbt] case class ModuleReportArtifactInfo(moduleReport: ModuleReport) extends IvyArtifactInfo {
  override def getLastModified: Long = moduleReport.publicationDate map { _.getTime } getOrElse 0L
  override def getRevision: String = moduleReport.module.revision
}
private[sbt] case class IvyOverride(moduleId: IvyModuleId, pm: PatternMatcher, ddm: OverrideDependencyDescriptorMediator) {
  override def toString: String = s"""IvyOverride($moduleId,$pm,${ddm.getVersion},${ddm.getBranch})"""
}
