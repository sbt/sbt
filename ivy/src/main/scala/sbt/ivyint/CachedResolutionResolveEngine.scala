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
import core.module.descriptor.{ DefaultModuleDescriptor, ModuleDescriptor, DependencyDescriptor, Configuration => IvyConfiguration }
import core.{ IvyPatternHelper, LogOptions }
import org.apache.ivy.util.Message
import org.apache.ivy.plugins.latest.{ ArtifactInfo => IvyArtifactInfo }
import org.json4s._

private[sbt] object CachedResolutionResolveCache {
  def createID(organization: String, name: String, revision: String) =
    ModuleRevisionId.newInstance(organization, name, revision)
  def sbtOrgTemp = "org.scala-sbt.temp"
}

class CachedResolutionResolveCache() {
  import CachedResolutionResolveCache._
  val updateReportCache: concurrent.Map[ModuleRevisionId, Either[ResolveException, UpdateReport]] = concurrent.TrieMap()
  val resolveReportCache: concurrent.Map[ModuleRevisionId, ResolveReport] = concurrent.TrieMap()
  val resolvePropertiesCache: concurrent.Map[ModuleRevisionId, String] = concurrent.TrieMap()
  val directDependencyCache: concurrent.Map[ModuleRevisionId, Vector[DependencyDescriptor]] = concurrent.TrieMap()
  val conflictCache: concurrent.Map[(ModuleID, ModuleID), (Vector[ModuleID], Vector[ModuleID], String)] = concurrent.TrieMap()
  val maxConflictCacheSize: Int = 10000

  def clean(md0: ModuleDescriptor, prOpt: Option[ProjectResolver]): Unit = {
    val mrid0 = md0.getModuleRevisionId
    val mds =
      if (mrid0.getOrganisation == sbtOrgTemp) Vector(md0)
      else buildArtificialModuleDescriptors(md0, prOpt) map { _._1 }
    mds foreach { md =>
      updateReportCache.remove(md.getModuleRevisionId)
      // todo: backport this
      directDependencyCache.remove(md.getModuleRevisionId)
    }
  }
  def directDependencies(md0: ModuleDescriptor): Vector[DependencyDescriptor] =
    directDependencyCache.getOrElseUpdate(md0.getModuleRevisionId, md0.getDependencies.toVector)

  def buildArtificialModuleDescriptors(md0: ModuleDescriptor, prOpt: Option[ProjectResolver]): Vector[(DefaultModuleDescriptor, Boolean)] =
    {
      def expandInternalDeps(dep: DependencyDescriptor): Vector[DependencyDescriptor] =
        prOpt map {
          _.getModuleDescriptor(dep.getDependencyRevisionId) match {
            case Some(internal) => directDependencies(internal) flatMap expandInternalDeps
            case _              => Vector(dep)
          }
        } getOrElse Vector(dep)
      val expanded = directDependencies(md0) flatMap expandInternalDeps
      val rootModuleConfigs = md0.getConfigurations.toVector
      expanded map { buildArtificialModuleDescriptor(_, rootModuleConfigs, prOpt) }
    }
  def isDependencyChanging(dd: DependencyDescriptor): Boolean =
    dd.isChanging || IvySbt.isChanging(dd.getDependencyRevisionId)
  def buildArtificialModuleDescriptor(dd: DependencyDescriptor, rootModuleConfigs: Vector[IvyConfiguration], prOpt: Option[ProjectResolver]): (DefaultModuleDescriptor, Boolean) =
    {
      val mrid = dd.getDependencyRevisionId
      val confMap = (dd.getModuleConfigurations map { conf =>
        conf + "->(" + dd.getDependencyConfigurations(conf).mkString(",") + ")"
      })
      val depsString = mrid.toString + ";" + confMap.mkString(";")
      val sha1 = Hash.toHex(Hash(depsString))
      val md1 = new DefaultModuleDescriptor(createID(sbtOrgTemp, "temp-resolve-" + sha1, "1.0"), "release", null, false) with ArtificialModuleDescriptor {
        def targetModuleRevisionId: ModuleRevisionId = mrid
      }
      for {
        conf <- rootModuleConfigs
      } yield md1.addConfiguration(conf)
      md1.addDependency(dd)
      (md1, isDependencyChanging(dd))
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
        else None) map { path =>
          log.debug(s"parsing ${path.getAbsolutePath.toString}")
          val ur = parseUpdateReport(md, path, cachedDescriptor, log)
          updateReportCache(md.getModuleRevisionId) = Right(ur)
          Right(ur)
        }
      (updateReportCache.get(mrid) orElse loadMiniGraphFromFile) match {
        case Some(result) => result
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
              writeUpdateReport(ur,
                if (changing) dynamicGraphPath
                else staticGraphPath)
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
  def parseUpdateReport(md: ModuleDescriptor, path: File, cachedDescriptor: File, log: Logger): UpdateReport =
    {
      import org.json4s._
      implicit val formats = native.Serialization.formats(NoTypeHints) +
        new ConfigurationSerializer +
        new ArtifactSerializer +
        new FileSerializer
      try {
        val json = jawn.support.json4s.Parser.parseFromFile(path)
        fromLite(json.get.extract[UpdateReportLite], cachedDescriptor)
      } catch {
        case e: Throwable =>
          log.error("Unable to parse mini graph: " + path.toString)
          throw e
      }
    }
  def writeUpdateReport(ur: UpdateReport, graphPath: File): Unit =
    {
      implicit val formats = native.Serialization.formats(NoTypeHints) +
        new ConfigurationSerializer +
        new ArtifactSerializer +
        new FileSerializer
      import native.Serialization.write
      println(graphPath.toString)
      val str = write(toLite(ur))
      IO.write(graphPath, str, IO.utf8)
    }
  def toLite(ur: UpdateReport): UpdateReportLite =
    UpdateReportLite(ur.configurations map { cr =>
      ConfigurationReportLite(cr.configuration, cr.details)
    })
  def fromLite(lite: UpdateReportLite, cachedDescriptor: File): UpdateReport =
    {
      val stats = new UpdateStats(0L, 0L, 0L, false)
      val configReports = lite.configurations map { cr =>
        val details = cr.details
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
        new ConfigurationReport(cr.configuration, modules, details, evicted)
      }
      new UpdateReport(cachedDescriptor, configReports, stats)
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
  class FileSerializer extends CustomSerializer[File](format => (
    {
      case JString(s) => new File(s)
    },
    {
      case x: File => JString(x.toString)
    }
  ))
  class ConfigurationSerializer extends CustomSerializer[Configuration](format => (
    {
      case JString(s) => new Configuration(s)
    },
    {
      case x: Configuration => JString(x.name)
    }
  ))
  class ArtifactSerializer extends CustomSerializer[Artifact](format => (
    {
      case json: JValue =>
        implicit val fmt = format
        Artifact(
          (json \ "name").extract[String],
          (json \ "type").extract[String],
          (json \ "extension").extract[String],
          (json \ "classifier").extract[Option[String]],
          (json \ "configurations").extract[List[Configuration]],
          (json \ "url").extract[Option[URL]],
          (json \ "extraAttributes").extract[Map[String, String]]
        )
    },
    {
      case x: Artifact =>
        import DefaultJsonFormats.{ OptionWriter, StringWriter, mapWriter }
        val optStr = implicitly[Writer[Option[String]]]
        val mw = implicitly[Writer[Map[String, String]]]
        JObject(JField("name", JString(x.name)) ::
          JField("type", JString(x.`type`)) ::
          JField("extension", JString(x.extension)) ::
          JField("classifier", optStr.write(x.classifier)) ::
          JField("configurations", JArray(x.configurations.toList map { x => JString(x.name) })) ::
          JField("url", optStr.write(x.url map { _.toString })) ::
          JField("extraAttributes", mw.write(x.extraAttributes)) ::
          Nil)
    }
  ))
}

private[sbt] trait ArtificialModuleDescriptor { self: DefaultModuleDescriptor =>
  def targetModuleRevisionId: ModuleRevisionId
}

private[sbt] case class UpdateReportLite(configurations: Seq[ConfigurationReportLite])
private[sbt] case class ConfigurationReportLite(configuration: String, details: Seq[OrganizationArtifactReport])

private[sbt] trait CachedResolutionResolveEngine extends ResolveEngine {
  import CachedResolutionResolveCache._

  private[sbt] def cachedResolutionResolveCache: CachedResolutionResolveCache
  private[sbt] def projectResolver: Option[ProjectResolver]
  private[sbt] def makeInstance: Ivy

  // Return sbt's UpdateReport.
  def customResolve(md0: ModuleDescriptor, logicalClock: LogicalClock, options0: ResolveOptions, depDir: File, log: Logger): Either[ResolveException, UpdateReport] = {
    import Path._
    val miniGraphPath = depDir / "module"
    val cachedDescriptor = getSettings.getResolutionCacheManager.getResolvedIvyFileInCache(md0.getModuleRevisionId)
    val cache = cachedResolutionResolveCache
    val mds = cache.buildArtificialModuleDescriptors(md0, projectResolver)
    def doWork(md: ModuleDescriptor): Either[ResolveException, UpdateReport] =
      {
        val options1 = new ResolveOptions(options0)
        val i = makeInstance
        var rr = i.resolve(md, options1)
        if (!rr.hasError) Right(IvyRetrieve.updateReport(rr, cachedDescriptor))
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
    val uReport = mergeResults(md0, results, log)
    val cacheManager = getSettings.getResolutionCacheManager
    cacheManager.saveResolvedModuleDescriptor(md0)
    val prop0 = ""
    val ivyPropertiesInCache0 = cacheManager.getResolvedIvyPropertiesInCache(md0.getResolvedModuleRevisionId)
    IO.write(ivyPropertiesInCache0, prop0)
    uReport
  }
  def mergeResults(md0: ModuleDescriptor, results: Vector[Either[ResolveException, UpdateReport]], log: Logger): Either[ResolveException, UpdateReport] =
    if (results exists { _.isLeft }) Left(mergeErrors(md0, results collect { case Left(re) => re }, log))
    else Right(mergeReports(md0, results collect { case Right(ur) => ur }, log))
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
  def mergeReports(md0: ModuleDescriptor, reports: Vector[UpdateReport], log: Logger): UpdateReport =
    {
      val cachedDescriptor = getSettings.getResolutionCacheManager.getResolvedIvyFileInCache(md0.getModuleRevisionId)
      val rootModuleConfigs = md0.getConfigurations.toVector
      val stats = new UpdateStats(0L, 0L, 0L, false)
      val configReports = rootModuleConfigs map { conf =>
        val crs = reports flatMap { _.configurations filter { _.configuration == conf.getName } }
        mergeConfigurationReports(conf.getName, crs, log)
      }
      new UpdateReport(cachedDescriptor, configReports, stats, Map.empty)
    }
  def mergeConfigurationReports(rootModuleConf: String, reports: Vector[ConfigurationReport], log: Logger): ConfigurationReport =
    {
      // get the details right, and the rest could be derived
      val details = mergeOrganizationArtifactReports(rootModuleConf, reports flatMap { _.details }, log)
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
  def mergeOrganizationArtifactReports(rootModuleConf: String, reports0: Vector[OrganizationArtifactReport], log: Logger): Vector[OrganizationArtifactReport] =
    (reports0 groupBy { oar => (oar.organization, oar.name) }).toSeq.toVector flatMap {
      case ((org, name), xs) =>
        if (xs.size < 2) xs
        else Vector(new OrganizationArtifactReport(org, name, mergeModuleReports(rootModuleConf, xs flatMap { _.modules }, log)))
    }
  def mergeModuleReports(rootModuleConf: String, modules: Vector[ModuleReport], log: Logger): Vector[ModuleReport] =
    {
      val merged = (modules groupBy { m => (m.module.organization, m.module.name, m.module.revision) }).toSeq.toVector flatMap {
        case ((org, name, version), xs) =>
          if (xs.size < 2) xs
          else Vector(xs.head.copy(evicted = xs exists { _.evicted }, callers = xs flatMap { _.callers }))
      }
      val conflicts = merged filter { m => !m.evicted && m.problem.isEmpty }
      if (conflicts.size < 2) merged
      else resolveConflict(rootModuleConf, conflicts, log) match {
        case (survivor, evicted) =>
          survivor ++ evicted ++ (merged filter { m => m.evicted || m.problem.isDefined })
      }
    }
  def resolveConflict(rootModuleConf: String, conflicts: Vector[ModuleReport], log: Logger): (Vector[ModuleReport], Vector[ModuleReport]) =
    {
      import org.apache.ivy.plugins.conflict.{ NoConflictManager, StrictConflictManager, LatestConflictManager }
      val head = conflicts.head
      val organization = head.module.organization
      val name = head.module.name
      log.debug(s"- conflict in $rootModuleConf:$organization:$name " + (conflicts map { _.module }).mkString("(", ", ", ")"))
      def useLatest(lcm: LatestConflictManager): (Vector[ModuleReport], Vector[ModuleReport], String) =
        conflicts find { m =>
          m.callers.exists { _.isForceDependency }
        } match {
          case Some(m) =>
            (Vector(m), conflicts filterNot { _ == m } map { _.copy(evicted = true, evictedReason = Some(lcm.toString)) }, lcm.toString)
          case None =>
            val strategy = lcm.getStrategy
            val infos = conflicts map { ModuleReportArtifactInfo(_) }
            Option(strategy.findLatest(infos.toArray, None.orNull)) match {
              case Some(ModuleReportArtifactInfo(m)) =>
                (Vector(m), conflicts filterNot { _ == m } map { _.copy(evicted = true, evictedReason = Some(lcm.toString)) }, lcm.toString)
              case _ => (conflicts, Vector(), lcm.toString)
            }
        }
      def doResolveConflict: (Vector[ModuleReport], Vector[ModuleReport], String) =
        getSettings.getConflictManager(IvyModuleId.newInstance(organization, name)) match {
          case ncm: NoConflictManager     => (conflicts, Vector(), ncm.toString)
          case _: StrictConflictManager   => sys.error((s"conflict was found in $rootModuleConf:$organization:$name " + (conflicts map { _.module }).mkString("(", ", ", ")")))
          case lcm: LatestConflictManager => useLatest(lcm)
          case conflictManager            => sys.error(s"Unsupported conflict manager $conflictManager")
        }
      if (conflicts.size == 2) {
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
