package sbt.internal.librarymanagement
package ivyint

import java.util.Date
import java.io.File
import java.text.SimpleDateFormat
import collection.concurrent
import collection.mutable
import collection.immutable.ListMap
import org.apache.ivy.Ivy
import org.apache.ivy.core
import core.resolve._
import core.module.id.{ ModuleRevisionId, ModuleId => IvyModuleId }
import core.report.ResolveReport
import core.module.descriptor.{
  DefaultModuleDescriptor,
  ModuleDescriptor,
  DefaultDependencyDescriptor,
  DependencyDescriptor,
  Configuration => IvyConfiguration,
  ExcludeRule,
  IncludeRule
}
import core.module.descriptor.{ OverrideDependencyDescriptorMediator, DependencyArtifactDescriptor }
import core.IvyPatternHelper
import org.apache.ivy.util.{ Message, MessageLogger }
import org.apache.ivy.plugins.latest.{ ArtifactInfo => IvyArtifactInfo }
import org.apache.ivy.plugins.matcher.{ MapMatcher, PatternMatcher }
import annotation.tailrec
import scala.concurrent.duration._
import sbt.io.{ DirectoryFilter, Hash, IO }
import sbt.librarymanagement._, syntax._
import sbt.util.Logger

private[sbt] object CachedResolutionResolveCache {
  def createID(organization: String, name: String, revision: String) =
    ModuleRevisionId.newInstance(organization, name, revision)
  def sbtOrgTemp = JsonUtil.sbtOrgTemp
  def graphVersion = "0.13.9C"
  val buildStartup: Long = System.currentTimeMillis
  lazy val todayStr: String = toYyyymmdd(buildStartup)
  lazy val tomorrowStr: String = toYyyymmdd(buildStartup + 1.day.toMillis)
  lazy val yesterdayStr: String = toYyyymmdd(buildStartup - 1.day.toMillis)
  def toYyyymmdd(timeSinceEpoch: Long): String = yyyymmdd.format(new Date(timeSinceEpoch))
  lazy val yyyymmdd: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
}

private[sbt] class CachedResolutionResolveCache {
  import CachedResolutionResolveCache._
  val updateReportCache: concurrent.Map[ModuleRevisionId, Either[ResolveException, UpdateReport]] =
    concurrent.TrieMap()
  // Used for subproject
  val projectReportCache
      : concurrent.Map[(ModuleRevisionId, LogicalClock), Either[ResolveException, UpdateReport]] =
    concurrent.TrieMap()
  val resolveReportCache: concurrent.Map[ModuleRevisionId, ResolveReport] = concurrent.TrieMap()
  val resolvePropertiesCache: concurrent.Map[ModuleRevisionId, String] = concurrent.TrieMap()
  val conflictCache
      : concurrent.Map[(ModuleID, ModuleID), (Vector[ModuleID], Vector[ModuleID], String)] =
    concurrent.TrieMap()
  val maxConflictCacheSize: Int = 1024
  val maxUpdateReportCacheSize: Int = 1024

  def clean(): Unit = updateReportCache.clear()

  def directDependencies(md0: ModuleDescriptor): Vector[DependencyDescriptor] =
    md0.getDependencies.toVector

  // Returns a vector of (module descriptor, changing, dd)
  def buildArtificialModuleDescriptors(
      md0: ModuleDescriptor,
      prOpt: Option[ProjectResolver],
      log: Logger
  ): Vector[(DefaultModuleDescriptor, Boolean, DependencyDescriptor)] = {
    log.debug(s":: building artificial module descriptors from ${md0.getModuleRevisionId}")
    // val expanded = expandInternalDependencies(md0, data, prOpt, log)
    val rootModuleConfigs = md0.getConfigurations.toVector
    directDependencies(md0) map { dd =>
      val arts = dd.getAllDependencyArtifacts.toVector map { x =>
        s"""${x.getName}:${x.getType}:${x.getExt}:${x.getExtraAttributes}"""
      }
      log.debug(s"::: dd: $dd (artifacts: ${arts.mkString(",")})")
      buildArtificialModuleDescriptor(dd, rootModuleConfigs, md0, prOpt)
    }
  }

  def internalDependency(
      dd: DependencyDescriptor,
      prOpt: Option[ProjectResolver]
  ): Option[ModuleDescriptor] =
    prOpt match {
      case Some(pr) => pr.getModuleDescriptor(dd.getDependencyRevisionId)
      case _        => None
    }

  def buildArtificialModuleDescriptor(
      dd: DependencyDescriptor,
      rootModuleConfigs: Vector[IvyConfiguration],
      parent: ModuleDescriptor,
      prOpt: Option[ProjectResolver]
  ): (DefaultModuleDescriptor, Boolean, DependencyDescriptor) = {
    def excludeRuleString(rule: ExcludeRule): String =
      s"""Exclude(${rule.getId},${rule.getConfigurations.mkString(",")},${rule.getMatcher})"""
    def includeRuleString(rule: IncludeRule): String =
      s"""Include(${rule.getId},${rule.getConfigurations.mkString(",")},${rule.getMatcher})"""
    def artifactString(dad: DependencyArtifactDescriptor): String =
      s"""Artifact(${dad.getName},${dad.getType},${dad.getExt},${dad.getUrl},${dad.getConfigurations
          .mkString(",")},${dad.getExtraAttributes})"""
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
    val depsString = s"""$mrid;${confMap.mkString(
        ","
      )};isForce=${dd.isForce};isChanging=${dd.isChanging};isTransitive=${dd.isTransitive};""" +
      s"""exclusions=${exclusions.mkString(",")};inclusions=${inclusions.mkString(
          ","
        )};explicitArtifacts=${explicitArtifacts
          .mkString(",")};$moduleLevel;"""
    val sha1 = Hash.toHex(
      Hash(s"""graphVersion=${CachedResolutionResolveCache.graphVersion};$depsString""")
    )
    val md1 = new DefaultModuleDescriptor(
      createID(sbtOrgTemp, "temp-resolve-" + sha1, "1.0"),
      "release",
      null,
      false
    ) with ArtificialModuleDescriptor {
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
  def extractOverrides(md0: ModuleDescriptor): Vector[IvyOverride] = {
    import scala.jdk.CollectionConverters._
    md0.getAllDependencyDescriptorMediators.getAllRules.asScala.toVector sortBy { case (k, _) =>
      k.toString
    } collect { case (k: MapMatcher, v: OverrideDependencyDescriptorMediator) =>
      val attr: Map[Any, Any] = k.getAttributes.asScala.toMap
      val module = IvyModuleId.newInstance(
        attr(IvyPatternHelper.ORGANISATION_KEY).toString,
        attr(IvyPatternHelper.MODULE_KEY).toString
      )
      val pm = k.getPatternMatcher
      IvyOverride(module, pm, v)
    }
  }
  def getOrElseUpdateMiniGraph(
      md: ModuleDescriptor,
      changing0: Boolean,
      logicalClock: LogicalClock,
      miniGraphPath: File,
      cachedDescriptor: File,
      log: Logger
  )(
      f: => Either[ResolveException, UpdateReport]
  ): Either[ResolveException, UpdateReport] = {
    import sbt.io.syntax._
    val mrid = md.getResolvedModuleRevisionId
    def extraPath(id: ModuleRevisionId, key: String, pattern: String): String =
      Option(id.getExtraAttribute(key)).fold(".")(pattern.format(_)) // "." has no affect on paths
    def scalaVersion(id: ModuleRevisionId): String = extraPath(id, "e:scalaVersion", "scala_%s")
    def sbtVersion(id: ModuleRevisionId): String = extraPath(id, "e:sbtVersion", "sbt_%s")
    val (pathOrg, pathName, pathRevision, pathScalaVersion, pathSbtVersion) = md match {
      case x: ArtificialModuleDescriptor =>
        val tmrid = x.targetModuleRevisionId
        (
          tmrid.getOrganisation,
          tmrid.getName,
          tmrid.getRevision + "_" + mrid.getName,
          scalaVersion(tmrid),
          sbtVersion(tmrid)
        )
      case _ =>
        (mrid.getOrganisation, mrid.getName, mrid.getRevision, scalaVersion(mrid), sbtVersion(mrid))
    }
    val staticGraphDirectory = miniGraphPath / "static"
    val dynamicGraphDirectory = miniGraphPath / "dynamic"
    val staticGraphPath =
      staticGraphDirectory / pathScalaVersion / pathSbtVersion / pathOrg / pathName / pathRevision / "graphs" / "graph.json"
    val dynamicGraphPath =
      dynamicGraphDirectory / todayStr / logicalClock.toString / pathScalaVersion / pathSbtVersion / pathOrg / pathName / pathRevision / "graphs" / "graph.json"
    def cleanDynamicGraph(): Unit = {
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
          val ur = JsonUtil.parseUpdateReport(path, cachedDescriptor, log)
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
            val gp =
              if (changing) dynamicGraphPath
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

  def getOrElseUpdateConflict(cf0: ModuleID, cf1: ModuleID, conflicts: Vector[ModuleReport])(
      f: => (Vector[ModuleReport], Vector[ModuleReport], String)
  ): (Vector[ModuleReport], Vector[ModuleReport]) = {
    def reconstructReports(
        surviving: Vector[ModuleID],
        evicted: Vector[ModuleID],
        mgr: String
    ): (Vector[ModuleReport], Vector[ModuleReport]) = {
      val moduleIdMap = Map(conflicts map { x =>
        x.module -> x
      }: _*)
      (
        surviving map moduleIdMap,
        evicted map moduleIdMap map {
          _.withEvicted(true).withEvictedReason(Some(mgr.toString))
        }
      )
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
  def getOrElseUpdateProjectReport(mrid: ModuleRevisionId, logicalClock: LogicalClock)(
      f: => Either[ResolveException, UpdateReport]
  ): Either[ResolveException, UpdateReport] =
    if (projectReportCache contains (mrid -> logicalClock)) projectReportCache((mrid, logicalClock))
    else {
      val oldKeys = projectReportCache.keys filter { case (_, clk) => clk != logicalClock }
      projectReportCache --= oldKeys
      projectReportCache.getOrElseUpdate((mrid, logicalClock), f)
    }
}

private[sbt] trait ArtificialModuleDescriptor { self: DefaultModuleDescriptor =>
  def targetModuleRevisionId: ModuleRevisionId
}

private[sbt] trait CachedResolutionResolveEngine extends ResolveEngine {
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
      try {
        f(ivy)
      } finally {
        ivy.getLoggerEngine.popLogger()
        ivy.popContext()
      }
    }
  def withDefaultLogger[A](log: MessageLogger)(f: => A): A = {
    val originalLogger = Message.getDefaultLogger
    Message.setDefaultLogger(log)
    try {
      f
    } finally {
      Message.setDefaultLogger(originalLogger)
    }
  }

  /**
   * This returns sbt's UpdateReport structure.
   * missingOk allows sbt to call this with classifiers that may or may not exist, and grab the JARs.
   */
  def customResolve(
      md0: ModuleDescriptor,
      missingOk: Boolean,
      logicalClock: LogicalClock,
      options0: ResolveOptions,
      depDir: File,
      log: Logger
  ): Either[ResolveException, UpdateReport] =
    cachedResolutionResolveCache.getOrElseUpdateProjectReport(
      md0.getModuleRevisionId,
      logicalClock
    ) {
      import sbt.io.syntax._
      val start = System.currentTimeMillis
      val miniGraphPath = depDir / "module"
      val cachedDescriptor =
        getSettings.getResolutionCacheManager.getResolvedIvyFileInCache(md0.getModuleRevisionId)
      val cache = cachedResolutionResolveCache
      val os = cache.extractOverrides(md0)
      val options1 = new ResolveOptions(options0)
      val data = new ResolveData(this, options1)
      val mds = cache.buildArtificialModuleDescriptors(md0, projectResolver, log)

      def doWork(
          md: ModuleDescriptor,
          dd: DependencyDescriptor
      ): Either[ResolveException, UpdateReport] =
        cache.internalDependency(dd, projectResolver) match {
          case Some(md1) =>
            log.debug(s":: call customResolve recursively: $dd")
            customResolve(md1, missingOk, logicalClock, options0, depDir, log) match {
              case Right(ur) =>
                Right(remapInternalProject(new IvyNode(data, md1), ur, md0, dd, os, log))
              case Left(e) => Left(e)
            }
          case None =>
            log.debug(s":: call ivy resolution: $dd")
            doWorkUsingIvy(md)
        }
      def doWorkUsingIvy(md: ModuleDescriptor): Either[ResolveException, UpdateReport] = {
        import scala.jdk.CollectionConverters._
        val options1 = new ResolveOptions(options0)
        val rr = withIvy(log) { ivy =>
          ivy.resolve(md, options1)
        }
        if (!rr.hasError || missingOk) Right(IvyRetrieve.updateReport(rr, cachedDescriptor))
        else {
          val messages = rr.getAllProblemMessages.asScala.toSeq.map(_.toString).distinct
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
      val (internal, external) = mds.partition { case (_, _, dd) =>
        cache.internalDependency(dd, projectResolver).isDefined
      }
      val internalResults = internal map { case (md, changing, dd) =>
        cache.getOrElseUpdateMiniGraph(
          md,
          changing,
          logicalClock,
          miniGraphPath,
          cachedDescriptor,
          log
        ) {
          doWork(md, dd)
        }
      }
      val externalResults = external map { case (md0, changing, dd) =>
        val configurationsInInternal = internalResults flatMap {
          case Right(ur) =>
            ur.allModules.flatMap { case md =>
              val sameName = md.name == dd.getDependencyId.getName
              val sameOrg = md.organization == dd.getDependencyId.getOrganisation
              if (sameName && sameOrg) md.configurations
              else None
            }
          case _ => Nil
        }

        dd match {
          case d: DefaultDependencyDescriptor =>
            configurationsInInternal foreach { c =>
              val configurations = c.split(";").map(_.split("->"))
              configurations foreach { conf =>
                try d.addDependencyConfiguration(conf(0), conf(1))
                catch {
                  case _: Throwable => ()
                } // An exception will be thrown if `conf(0)` doesn't exist.
              }
            }

          case _ => ()
        }

        cache.getOrElseUpdateMiniGraph(
          md0,
          changing,
          logicalClock,
          miniGraphPath,
          cachedDescriptor,
          log
        ) {
          doWork(md0, dd)
        }
      }
      val results = internalResults ++ externalResults
      val uReport =
        mergeResults(md0, results, missingOk, System.currentTimeMillis - start, os, log)
      val cacheManager = getSettings.getResolutionCacheManager
      cacheManager.saveResolvedModuleDescriptor(md0)
      val prop0 = ""
      val ivyPropertiesInCache0 =
        cacheManager.getResolvedIvyPropertiesInCache(md0.getResolvedModuleRevisionId)
      IO.write(ivyPropertiesInCache0, prop0)
      uReport
    }

  def mergeResults(
      md0: ModuleDescriptor,
      results: Vector[Either[ResolveException, UpdateReport]],
      missingOk: Boolean,
      resolveTime: Long,
      os: Vector[IvyOverride],
      log: Logger
  ): Either[ResolveException, UpdateReport] =
    if (!missingOk && (results exists { _.isLeft }))
      Left(mergeErrors(md0, results collect { case Left(re) => re }))
    else Right(mergeReports(md0, results collect { case Right(ur) => ur }, resolveTime, os, log))

  def mergeErrors(md0: ModuleDescriptor, errors: Vector[ResolveException]): ResolveException = {
    val messages = errors flatMap { _.messages }
    val failed = errors flatMap { _.failed }
    val failedPaths = errors flatMap {
      _.failedPaths.toList map { case (failed, paths) =>
        if (paths.isEmpty) (failed, paths)
        else
          (
            failed,
            List(IvyRetrieve.toModuleID(md0.getResolvedModuleRevisionId)) ::: paths.toList.tail
          )
      }
    }
    new ResolveException(messages, failed, ListMap(failedPaths: _*))
  }

  def mergeReports(
      md0: ModuleDescriptor,
      reports: Vector[UpdateReport],
      resolveTime: Long,
      os: Vector[IvyOverride],
      log: Logger
  ): UpdateReport = {
    log.debug(s":: merging update reports")
    val cachedDescriptor =
      getSettings.getResolutionCacheManager.getResolvedIvyFileInCache(md0.getModuleRevisionId)
    val rootModuleConfigs = md0.getConfigurations.toVector
    val cachedReports = reports filter { !_.stats.cached }
    val stats = UpdateStats(
      resolveTime,
      (cachedReports map { _.stats.downloadTime }).sum,
      (cachedReports map { _.stats.downloadSize }).sum,
      false
    )
    val configReports = rootModuleConfigs map { conf =>
      log.debug("::: -----------")
      val crs = reports flatMap {
        _.configurations filter { _.configuration.name == conf.getName }
      }
      mergeConfigurationReports(ConfigRef(conf.getName), crs, os, log)
    }
    UpdateReport(cachedDescriptor, configReports, stats, Map.empty)
  }

  // memory usage 62%, of which 58% is in mergeOrganizationArtifactReports
  def mergeConfigurationReports(
      rootModuleConf: ConfigRef,
      reports: Vector[ConfigurationReport],
      os: Vector[IvyOverride],
      log: Logger
  ): ConfigurationReport = {
    // get the details right, and the rest could be derived
    val details =
      mergeOrganizationArtifactReports(rootModuleConf, reports flatMap { _.details }, os, log)
    val modules = details flatMap {
      _.modules filter { mr =>
        !mr.evicted && mr.problem.isEmpty
      }
    }
    ConfigurationReport(rootModuleConf, modules, details)
  }

  /**
   * Returns a tuple of (merged org + name combo, newly evicted modules)
   */
  def mergeOrganizationArtifactReports(
      rootModuleConf: ConfigRef,
      reports0: Vector[OrganizationArtifactReport],
      os: Vector[IvyOverride],
      log: Logger
  ): Vector[OrganizationArtifactReport] = {
    // filter out evicted modules from further logic
    def filterReports(report0: OrganizationArtifactReport): Option[OrganizationArtifactReport] =
      report0.modules.toVector flatMap { mr =>
        if (mr.evicted || mr.problem.nonEmpty) None
        else
          // https://github.com/sbt/sbt/issues/1763
          Some(mr.withCallers(JsonUtil.filterOutArtificialCallers(mr.callers)))
      } match {
        case Vector() => None
        case ms       => Some(OrganizationArtifactReport(report0.organization, report0.name, ms))
      }

    // group by takes up too much memory. trading space with time.
    val orgNamePairs: Vector[(String, String)] = (reports0 map { oar =>
      (oar.organization, oar.name)
    }).distinct
    // this might take up some memory, but it's limited to a single
    val reports1 = reports0 flatMap { filterReports }
    val allModules0: Map[(String, String), Vector[OrganizationArtifactReport]] =
      Map(orgNamePairs map { case (organization, name) =>
        val xs = reports1 filter { oar =>
          oar.organization == organization && oar.name == name
        }
        ((organization, name), xs)
      }: _*)
    // this returns a List of Lists of (org, name). should be deterministic
    def detectLoops(
        allModules: Map[(String, String), Vector[OrganizationArtifactReport]]
    ): List[List[(String, String)]] = {
      val loopSets: mutable.Set[Set[(String, String)]] = mutable.Set.empty
      val loopLists: mutable.ListBuffer[List[(String, String)]] = mutable.ListBuffer.empty
      def testLoop(
          m: (String, String),
          current: (String, String),
          history: List[(String, String)]
      ): Unit = {
        val callers =
          (for {
            oar <- allModules.getOrElse(current, Vector())
            mr <- oar.modules
            c <- mr.callers
          } yield (c.caller.organization, c.caller.name)).distinct
        callers foreach { c =>
          if (history.contains[(String, String)](c)) {
            val loop = (c :: history.takeWhile(_ != c)) ::: List(c)
            if (!loopSets(loop.toSet)) {
              loopSets += loop.toSet
              loopLists += loop
              val loopStr = (loop map { case (o, n) => s"$o:$n" }).mkString("->")
              log.warn(s"""avoid circular dependency while using cached resolution: $loopStr""")
            }
          } else testLoop(m, c, c :: history)
        }
      }
      orgNamePairs map { orgname =>
        testLoop(orgname, orgname, List(orgname))
      }
      loopLists.toList
    }
    val allModules2: mutable.Map[(String, String), Vector[OrganizationArtifactReport]] =
      mutable.Map(allModules0.toSeq: _*)
    @tailrec def breakLoops(loops: List[List[(String, String)]]): Unit =
      loops match {
        case Nil => ()
        case loop :: rest =>
          loop match {
            case Nil =>
              breakLoops(rest)
            case loop =>
              val sortedLoop = loop sortBy { x =>
                (for {
                  oar <- allModules0(x)
                  mr <- oar.modules
                  c <- mr.callers
                } yield c).size
              }
              val moduleWithMostCallers = sortedLoop.reverse.head
              val next: (String, String) = loop(loop.indexOf(moduleWithMostCallers) + 1)
              // remove the module with most callers as the caller of next.
              // so, A -> C, B -> C, and C -> A. C has the most callers, and C -> A will be removed.
              allModules2 foreach {
                case (k: (String, String), oars0) if k == next =>
                  val oars: Vector[OrganizationArtifactReport] = oars0 map { oar =>
                    val mrs = oar.modules map { mr =>
                      val callers0 = mr.callers
                      val callers = callers0 filterNot { c =>
                        (c.caller.organization, c.caller.name) == moduleWithMostCallers
                      }
                      if (callers.size == callers0.size) mr
                      else {
                        log.debug(
                          s":: $rootModuleConf: removing caller $moduleWithMostCallers -> $next for sorting"
                        )
                        mr.withCallers(callers)
                      }
                    }
                    OrganizationArtifactReport(oar.organization, oar.name, mrs)
                  }
                  allModules2(k) = oars
                case (_, _) => // do nothing
              }

              breakLoops(rest)
          }
      }
    val loop = detectLoops(allModules0)
    log.debug(s":: $rootModuleConf: loop: $loop")
    breakLoops(loop)

    // sort the all modules such that less called modules comes earlier
    @tailrec
    def sortModules(
        cs: Vector[(String, String)],
        acc: Vector[(String, String)],
        extra: Vector[(String, String)],
        n: Int,
        guard: Int
    ): Vector[(String, String)] = {
      // println(s"sortModules: $n / $guard")
      val keys = cs.toSet
      val (called, notCalled) = cs partition { k =>
        val reports = allModules2(k)
        reports exists {
          _.modules.exists {
            _.callers exists { caller =>
              val m = caller.caller
              keys((m.organization, m.name))
            }
          }
        }
      }
      lazy val result0 = acc ++ notCalled ++ called ++ extra
      def warnCircular(): Unit = {
        log.warn(
          s"""unexpected circular dependency while using cached resolution: ${cs.mkString(",")}"""
        )
      }
      (if (n > guard) {
         warnCircular()
         result0
       } else if (called.isEmpty) result0
       else if (notCalled.isEmpty) {
         warnCircular()
         sortModules(cs.tail, acc, extra :+ cs.head, n + 1, guard)
       } else sortModules(called, acc ++ notCalled, extra, 0, called.size * called.size + 1))
    }
    def resolveConflicts(
        cs: List[(String, String)],
        allModules: Map[(String, String), Vector[OrganizationArtifactReport]]
    ): List[OrganizationArtifactReport] =
      cs match {
        case Nil => Nil
        case (organization, name) :: rest =>
          val reports = allModules((organization, name))
          reports match {
            case Vector()                           => resolveConflicts(rest, allModules)
            case Vector(oa) if (oa.modules.isEmpty) => resolveConflicts(rest, allModules)
            case Vector(oa) if (oa.modules.size == 1 && !oa.modules.head.evicted) =>
              log.debug(s":: no conflict $rootModuleConf: ${oa.organization}:${oa.name}")
              oa :: resolveConflicts(rest, allModules)
            case oas =>
              (mergeModuleReports(rootModuleConf, oas flatMap { _.modules }, os, log) match {
                case (survivor, newlyEvicted) =>
                  val evicted = (survivor ++ newlyEvicted) filter { m =>
                    m.evicted
                  }
                  val notEvicted = (survivor ++ newlyEvicted) filter { m =>
                    !m.evicted
                  }
                  log.debug("::: adds " + (notEvicted map { _.module }).mkString(", "))
                  log.debug("::: evicted " + (evicted map { _.module }).mkString(", "))
                  val x = OrganizationArtifactReport(organization, name, survivor ++ newlyEvicted)
                  val nextModules =
                    transitivelyEvict(rootModuleConf, rest, allModules, evicted, log)
                  x :: resolveConflicts(rest, nextModules)
              })
          }
      }
    val guard0 = (orgNamePairs.size * orgNamePairs.size) + 1
    val sorted: Vector[(String, String)] = sortModules(orgNamePairs, Vector(), Vector(), 0, guard0)
    val sortedStr = (sorted map { case (o, n) => s"$o:$n" }).mkString(", ")
    log.debug(s":: sort result: $sortedStr")
    val result = resolveConflicts(sorted.toList, allModules0)
    result.toVector
  }

  /**
   * Merges ModuleReports, which represents orgnization, name, and version.
   * Returns a touple of (surviving modules ++ non-conflicting modules, newly evicted modules).
   */
  def mergeModuleReports(
      rootModuleConf: ConfigRef,
      modules: Vector[ModuleReport],
      os: Vector[IvyOverride],
      log: Logger
  ): (Vector[ModuleReport], Vector[ModuleReport]) = {
    if (modules.nonEmpty) {
      log.debug(
        s":: merging module reports for $rootModuleConf: ${modules.head.module.organization}:${modules.head.module.name}"
      )
    }
    def mergeModuleReports(xs: Vector[ModuleReport]): ModuleReport = {
      val completelyEvicted = xs forall { _.evicted }
      val allCallers = xs flatMap { _.callers }
      // Caller info is often repeated across the subprojects. We only need ModuleID info for later, so xs.head is ok.
      val distinctByModuleId = allCallers.groupBy({ _.caller }).toVector map { case (_, xs) =>
        xs.head
      }
      val allArtifacts = (xs flatMap { _.artifacts }).distinct
      xs.head
        .withArtifacts(allArtifacts)
        .withEvicted(completelyEvicted)
        .withCallers(distinctByModuleId)
    }
    val merged = (modules groupBy { m =>
      (m.module.organization, m.module.name, m.module.revision)
    }).toVector flatMap { case (_, xs) =>
      if (xs.size < 2) xs
      else Vector(mergeModuleReports(xs))
    }
    val conflicts = merged filter { m =>
      !m.evicted && m.problem.isEmpty
    }
    if (conflicts.size < 2) (merged, Vector())
    else
      resolveConflict(rootModuleConf, conflicts, os, log) match {
        case (survivor, evicted) =>
          (
            survivor ++ (merged filter { m =>
              m.evicted || m.problem.isDefined
            }),
            evicted
          )
      }
  }

  /**
   * This transitively evicts any non-evicted modules whose only callers are newly evicted.
   */
  def transitivelyEvict(
      rootModuleConf: ConfigRef,
      pairs: List[(String, String)],
      reports0: Map[(String, String), Vector[OrganizationArtifactReport]],
      evicted0: Vector[ModuleReport],
      log: Logger
  ): Map[(String, String), Vector[OrganizationArtifactReport]] = {
    val em = (evicted0 map { _.module }).toSet
    def isTransitivelyEvicted(mr: ModuleReport): Boolean =
      mr.callers forall { c =>
        em(c.caller)
      }
    val reports: Seq[((String, String), Vector[OrganizationArtifactReport])] =
      reports0.toSeq flatMap {
        case (k, _) if !(pairs.contains[(String, String)](k)) => Seq()
        case ((organization, name), oars0) =>
          val oars = oars0 map { oar =>
            val (affected, unaffected) = oar.modules partition { mr =>
              val x = !mr.evicted && mr.problem.isEmpty && isTransitivelyEvicted(mr)
              if (x) {
                log.debug(s""":::: transitively evicted $rootModuleConf: ${mr.module}""")
              }
              x
            }
            val newlyEvicted = affected map {
              _.withEvicted(true).withEvictedReason(Some("transitive-evict"))
            }
            if (affected.isEmpty) oar
            else OrganizationArtifactReport(organization, name, unaffected ++ newlyEvicted)
          }
          Seq(((organization, name), oars))
      }
    Map(reports: _*)
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
  def resolveConflict(
      rootModuleConf: ConfigRef,
      conflicts: Vector[ModuleReport],
      os: Vector[IvyOverride],
      log: Logger
  ): (Vector[ModuleReport], Vector[ModuleReport]) = {
    import org.apache.ivy.plugins.conflict.{
      NoConflictManager,
      StrictConflictManager,
      LatestConflictManager
    }
    val head = conflicts.head
    val organization = head.module.organization
    val name = head.module.name
    log.debug(s"::: resolving conflict in $rootModuleConf:$organization:$name " + (conflicts map {
      _.module
    }).mkString("(", ", ", ")"))
    def useLatest(
        lcm: LatestConflictManager
    ): (Vector[ModuleReport], Vector[ModuleReport], String) =
      (conflicts find { m =>
        m.callers.exists { _.isDirectlyForceDependency }
      }) match {
        case Some(m) =>
          log.debug(s"- directly forced dependency: $m ${m.callers}")
          (
            Vector(m),
            conflicts filterNot { _ == m } map {
              _.withEvicted(true).withEvictedReason(Some("direct-force"))
            },
            "direct-force"
          )
        case None =>
          (conflicts find { m =>
            m.callers.exists { _.isForceDependency }
          }) match {
            // Ivy translates pom.xml dependencies to forced="true", so transitive force is broken.
            case Some(m) if !ignoreTransitiveForce =>
              log.debug(s"- transitively forced dependency: $m ${m.callers}")
              (
                Vector(m),
                conflicts filterNot { _ == m } map {
                  _.withEvicted(true).withEvictedReason(Some("transitive-force"))
                },
                "transitive-force"
              )
            case _ =>
              val strategy = lcm.getStrategy
              val infos = conflicts map { ModuleReportArtifactInfo(_) }
              log.debug(s"- Using $strategy with $infos")
              Option(strategy.findLatest(infos.toArray, None.orNull)) match {
                case Some(ModuleReportArtifactInfo(m)) =>
                  (
                    Vector(m),
                    conflicts filterNot { _ == m } map {
                      _.withEvicted(true).withEvictedReason(Some(lcm.toString))
                    },
                    lcm.toString
                  )
                case _ => (conflicts, Vector(), lcm.toString)
              }
          }
      }
    def doResolveConflict: (Vector[ModuleReport], Vector[ModuleReport], String) =
      os find { ovr =>
        ovr.moduleId.getOrganisation == organization && ovr.moduleId.getName == name
      } match {
        case Some(ovr) if Option(ovr.ddm.getVersion).isDefined =>
          val ovrVersion = ovr.ddm.getVersion
          conflicts find { mr =>
            mr.module.revision == ovrVersion
          } match {
            case Some(m) =>
              (
                Vector(m),
                conflicts filterNot { _ == m } map {
                  _.withEvicted(true).withEvictedReason(Some("override"))
                },
                "override"
              )
            case None =>
              sys.error(
                s"override dependency specifies $ovrVersion but no candidates were found: " + (conflicts map {
                  _.module
                }).mkString("(", ", ", ")")
              )
          }
        case _ =>
          getSettings.getConflictManager(IvyModuleId.newInstance(organization, name)) match {
            case ncm: NoConflictManager => (conflicts, Vector(), ncm.toString)
            case _: StrictConflictManager =>
              sys.error(
                (s"conflict was found in $rootModuleConf:$organization:$name " + (conflicts map {
                  _.module
                }).mkString("(", ", ", ")"))
              )
            case lcm: LatestConflictManager => useLatest(lcm)
            case conflictManager => sys.error(s"Unsupported conflict manager $conflictManager")
          }
      }
    if (conflicts.size == 2 && os.isEmpty) {
      val (cf0, cf1) = (conflicts(0).module, conflicts(1).module)
      val cache = cachedResolutionResolveCache
      val (surviving, evicted) = cache.getOrElseUpdateConflict(cf0, cf1, conflicts) {
        doResolveConflict
      }
      (surviving, evicted)
    } else {
      val (surviving, evicted, _) = doResolveConflict
      (surviving, evicted)
    }
  }
  def remapInternalProject(
      node: IvyNode,
      ur: UpdateReport,
      md0: ModuleDescriptor,
      dd: DependencyDescriptor,
      os: Vector[IvyOverride],
      log: Logger
  ): UpdateReport = {
    def parentConfigs(c: String): Vector[String] =
      Option(md0.getConfiguration(c)) match {
        case Some(config) =>
          config.getExtends.toVector ++
            (config.getExtends.toVector flatMap parentConfigs)
        case None => Vector()
      }
    // These are the configurations from the original project we want to resolve.
    val rootModuleConfs = md0.getConfigurations.toVector
    val configurations0: Vector[ConfigurationReport] = ur.configurations.toVector
    // This is how md looks from md0 via dd's mapping.
    val remappedConfigs0: Map[String, Vector[String]] = Map(rootModuleConfs map { conf0 =>
      val remapped: Vector[String] = dd
        .getDependencyConfigurations(conf0.getName)
        .toVector flatMap { conf =>
        node.getRealConfs(conf).toVector
      }
      conf0.getName -> remapped
    }: _*)
    // This emulates test-internal extending test configuration etc.
    val remappedConfigs: Map[String, Vector[String]] =
      rootModuleConfs.foldLeft(remappedConfigs0) { (acc0, c) =>
        val ps = parentConfigs(c.getName)
        ps.foldLeft(acc0) { (acc, parent) =>
          val vs0 = acc.getOrElse(c.getName, Vector())
          val vs = acc.getOrElse(parent, Vector())
          acc.updated(c.getName, (vs0 ++ vs).distinct)
        }
      }
    log.debug(s"::: remapped configs $remappedConfigs")
    val configurations = rootModuleConfs map { conf0 =>
      val remappedCRs: Vector[ConfigurationReport] = configurations0 filter { cr =>
        remappedConfigs(conf0.getName).contains[String](cr.configuration.name)
      }
      mergeConfigurationReports(ConfigRef(conf0.getName), remappedCRs, os, log)
    }
    UpdateReport(ur.cachedDescriptor, configurations, ur.stats, ur.stamps)
  }
}

private[sbt] case class ModuleReportArtifactInfo(moduleReport: ModuleReport)
    extends IvyArtifactInfo {
  override def getLastModified: Long =
    moduleReport.publicationDate map { _.getTimeInMillis } getOrElse 0L
  override def getRevision: String = moduleReport.module.revision
  override def toString: String =
    s"ModuleReportArtifactInfo(${moduleReport.module}, $getRevision, $getLastModified)"
}
private[sbt] case class IvyOverride(
    moduleId: IvyModuleId,
    pm: PatternMatcher,
    ddm: OverrideDependencyDescriptorMediator
) {
  override def toString: String =
    s"""IvyOverride($moduleId,$pm,${ddm.getVersion},${ddm.getBranch})"""
}
