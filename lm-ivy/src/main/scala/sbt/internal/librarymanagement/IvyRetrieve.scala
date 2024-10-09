/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt.internal.librarymanagement

import java.io.File
import java.{ util => ju }
import collection.mutable
import collection.immutable.ArraySeq
import org.apache.ivy.core.{ module, report, resolve }
import module.descriptor.{ Artifact => IvyArtifact, License => IvyLicense }
import module.id.{ ModuleRevisionId, ModuleId => IvyModuleId }
import report.{ ArtifactDownloadReport, ConfigurationResolveReport, ResolveReport }
import resolve.{ IvyNode, IvyNodeCallers }
import IvyNodeCallers.{ Caller => IvyCaller }
import ivyint.SbtDefaultDependencyDescriptor
import sbt.librarymanagement._, syntax._

object IvyRetrieve {
  def reports(report: ResolveReport): Vector[ConfigurationResolveReport] =
    report.getConfigurations.toVector map report.getConfigurationReport

  def moduleReports(confReport: ConfigurationResolveReport): Vector[ModuleReport] =
    for {
      revId <- confReport.getModuleRevisionIds.toArray.toVector collect {
        case revId: ModuleRevisionId => revId
      }
    } yield moduleRevisionDetail(confReport, confReport.getDependency(revId))

  @deprecated("Internal only. No longer in use.", "0.13.6")
  def artifactReports(mid: ModuleID, artReport: Seq[ArtifactDownloadReport]): ModuleReport = {
    val (resolved, missing) = artifacts(artReport)
    ModuleReport(mid, resolved, missing)
  }

  private[sbt] def artifacts(
      artReport: Seq[ArtifactDownloadReport]
  ): (Vector[(Artifact, File)], Vector[Artifact]) = {
    val missing = new mutable.ListBuffer[Artifact]
    val resolved = new mutable.ListBuffer[(Artifact, File)]
    for (r <- artReport) {
      val fileOpt = Option(r.getLocalFile)
      val art = toArtifact(r.getArtifact)
      fileOpt match {
        case Some(file) => resolved += ((art, file))
        case None       => missing += art
      }
    }
    (resolved.toVector, missing.toVector)
  }

  // We need this because current module report used as part of UpdateReport/ConfigurationReport contains
  // only the revolved modules.
  // Sometimes the entire module can be excluded via rules etc.
  private[sbt] def organizationArtifactReports(
      confReport: ConfigurationResolveReport
  ): Vector[OrganizationArtifactReport] = {
    val moduleIds = confReport.getModuleIds.toArray.toVector collect { case mId: IvyModuleId =>
      mId
    }
    def organizationArtifact(mid: IvyModuleId): OrganizationArtifactReport = {
      val deps = confReport.getNodes(mid).toArray.toVector collect { case node: IvyNode => node }
      OrganizationArtifactReport(
        mid.getOrganisation,
        mid.getName,
        deps map {
          moduleRevisionDetail(confReport, _)
        }
      )
    }
    moduleIds map { organizationArtifact }
  }

  private[sbt] def nonEmptyString(s: String): Option[String] =
    s match {
      case null              => None
      case x if x.trim == "" => None
      case x                 => Some(x.trim)
    }

  private[sbt] def moduleRevisionDetail(
      confReport: ConfigurationResolveReport,
      dep: IvyNode
  ): ModuleReport = {
    def toExtraAttributes(ea: ju.Map[_, _]): Map[String, String] =
      Map(ea.entrySet.toArray collect {
        case entry: ju.Map.Entry[_, _]
            if nonEmptyString(entry.getKey.toString).isDefined && nonEmptyString(
              entry.getValue.toString
            ).isDefined =>
          (entry.getKey.toString, entry.getValue.toString)
      }: _*)
    def toCaller(caller: IvyCaller): Caller = {
      val m = toModuleID(caller.getModuleRevisionId)
      val callerConfigurations = caller.getCallerConfigurations.toVector collect {
        case x if nonEmptyString(x).isDefined => ConfigRef(x)
      }
      val ddOpt = Option(caller.getDependencyDescriptor)
      val (extraAttributes, isForce, isChanging, isTransitive, isDirectlyForce) = ddOpt match {
        case Some(dd: SbtDefaultDependencyDescriptor) =>
          val mod = dd.dependencyModuleId
          (
            toExtraAttributes(dd.getExtraAttributes),
            mod.isForce,
            mod.isChanging,
            mod.isTransitive,
            mod.isForce
          )
        case Some(dd) =>
          (
            toExtraAttributes(dd.getExtraAttributes),
            dd.isForce,
            dd.isChanging,
            dd.isTransitive,
            false
          )
        case None => (Map.empty[String, String], false, false, true, false)
      }
      Caller(
        m,
        callerConfigurations,
        extraAttributes,
        isForce,
        isChanging,
        isTransitive,
        isDirectlyForce
      )
    }
    val revId = dep.getResolvedId
    val moduleId = toModuleID(revId)
    val branch = nonEmptyString(revId.getBranch)
    val (status, publicationDate, resolver, artifactResolver) = dep.isLoaded match {
      case true =>
        val c = new ju.GregorianCalendar()
        c.setTimeInMillis(dep.getPublication)
        (
          nonEmptyString(dep.getDescriptor.getStatus),
          Some(c),
          nonEmptyString(dep.getModuleRevision.getResolver.getName),
          nonEmptyString(dep.getModuleRevision.getArtifactResolver.getName)
        )
      case _ => (None, None, None, None)
    }
    val (evicted, evictedData, evictedReason) = dep.isEvicted(confReport.getConfiguration) match {
      case true =>
        val edOpt = Option(dep.getEvictedData(confReport.getConfiguration))
        edOpt match {
          case Some(ed) =>
            (
              true,
              nonEmptyString(Option(ed.getConflictManager) map { _.toString } getOrElse {
                "transitive"
              }),
              nonEmptyString(ed.getDetail)
            )
          case None => (true, None, None)
        }
      case _ => (false, None, None)
    }
    val problem = dep.hasProblem match {
      case true => nonEmptyString(dep.getProblem.getMessage)
      case _    => None
    }
    val mdOpt = for {
      mr <- Option(dep.getModuleRevision)
      md <- Option(mr.getDescriptor)
    } yield md
    val homepage = mdOpt match {
      case Some(md) =>
        nonEmptyString(md.getHomePage)
      case _ => None
    }
    val extraAttributes: Map[String, String] = toExtraAttributes(mdOpt match {
      case Some(md) => md.getExtraAttributes
      case _        => dep.getResolvedId.getExtraAttributes
    })
    val isDefault = Option(dep.getDescriptor) map { _.isDefault }
    val configurations = dep.getConfigurations(confReport.getConfiguration).toVector map {
      ConfigRef(_)
    }
    val licenses: Vector[(String, Option[String])] = mdOpt match {
      case Some(md) =>
        md.getLicenses.toVector collect {
          case lic: IvyLicense if Option(lic.getName).isDefined =>
            val temporaryURL = "http://localhost"
            (lic.getName, nonEmptyString(lic.getUrl) orElse { Some(temporaryURL) })
        }
      case _ => Vector.empty
    }
    val callers = dep.getCallers(confReport.getConfiguration).toVector map { toCaller }
    val (resolved, missing) = artifacts(
      ArraySeq.unsafeWrapArray(confReport.getDownloadReports(revId))
    )

    ModuleReport(
      moduleId,
      resolved,
      missing,
      status,
      publicationDate,
      resolver,
      artifactResolver,
      evicted,
      evictedData,
      evictedReason,
      problem,
      homepage,
      extraAttributes,
      isDefault,
      branch,
      configurations,
      licenses,
      callers
    )
  }

  def evicted(confReport: ConfigurationResolveReport): Seq[ModuleID] =
    ArraySeq.unsafeWrapArray(confReport.getEvictedNodes).map(node => toModuleID(node.getId))

  def toModuleID(revID: ModuleRevisionId): ModuleID =
    ModuleID(revID.getOrganisation, revID.getName, revID.getRevision)
      .withExtraAttributes(IvySbt.getExtraAttributes(revID))
      .branch(nonEmptyString(revID.getBranch))

  def toArtifact(art: IvyArtifact): Artifact = {
    import art._
    Artifact(
      getName,
      getType,
      getExt,
      Option(getExtraAttribute("classifier")),
      getConfigurations.toVector map { (c: String) =>
        ConfigRef(c)
      },
      Option(getUrl).map(_.toURI)
    )
  }

  def updateReport(report: ResolveReport, cachedDescriptor: File): UpdateReport =
    UpdateReport(
      cachedDescriptor,
      reports(report) map configurationReport,
      updateStats(report),
      Map.empty
    ).recomputeStamps()
  def updateStats(report: ResolveReport): UpdateStats =
    UpdateStats(report.getResolveTime, report.getDownloadTime, report.getDownloadSize, false)
  def configurationReport(confReport: ConfigurationResolveReport): ConfigurationReport =
    ConfigurationReport(
      ConfigRef(confReport.getConfiguration),
      moduleReports(confReport),
      organizationArtifactReports(confReport)
    )

  /**
   * Tries to find Ivy graph path the from node to target.
   */
  def findPath(target: IvyNode, from: ModuleRevisionId): List[IvyNode] = {
    def doFindPath(current: IvyNode, path: List[IvyNode]): List[IvyNode] = {
      // Ivy actually returns mix of direct and non-direct callers here.
      // that's why we have to calculate all possible paths below and pick the longest path.
      val callers = current.getAllRealCallers.toList
      val callersRevId = (callers map { _.getModuleRevisionId }).distinct
      val paths: List[List[IvyNode]] = ((callersRevId map { revId =>
        val node = current.findNode(revId)
        if (revId == from) node :: path
        else if (node == node.getRoot) Nil
        else if (path.contains[IvyNode](node)) path
        else doFindPath(node, node :: path)
      }) sortBy { _.size }).reverse
      paths.headOption getOrElse Nil
    }
    if (target.getId == from) List(target)
    else doFindPath(target, List(target))
  }
}
