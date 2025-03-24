package sbt.internal.librarymanagement

import java.io.File
import sbt.util.{ CacheStore, Logger }
import sbt.librarymanagement.*, LibraryManagementCodec.*
import sbt.io.IO

private[sbt] object JsonUtil {
  def sbtOrgTemp = "org.scala-sbt.temp"
  def fakeCallerOrganization = "org.scala-sbt.temp-callers"

  def parseUpdateReport(
      path: File,
      cachedDescriptor: File,
      log: Logger
  ): UpdateReport = {
    try {
      val lite = CacheStore(path).read[UpdateReportLite]()
      fromLite(lite, cachedDescriptor)
    } catch {
      case e: Throwable =>
        log.error(s"Unable to parse mini graph: $path")
        throw e
    }
  }

  def writeUpdateReport(ur: UpdateReport, graphPath: File): Unit = {
    val updateReportLite = toLite(ur)
    IO.createDirectory(graphPath.getParentFile)
    CacheStore(graphPath).write(updateReportLite)
  }

  def toLite(ur: UpdateReport): UpdateReportLite =
    UpdateReportLite(ur.configurations map { cr =>
      ConfigurationReportLite(
        cr.configuration.name,
        cr.details map { oar =>
          OrganizationArtifactReport(
            oar.organization,
            oar.name,
            oar.modules map { mr =>
              ModuleReport(
                mr.module,
                mr.artifacts,
                mr.missingArtifacts,
                mr.status,
                mr.publicationDate,
                mr.resolver,
                mr.artifactResolver,
                mr.evicted,
                mr.evictedData,
                mr.evictedReason,
                mr.problem,
                mr.homepage,
                mr.extraAttributes,
                mr.isDefault,
                mr.branch,
                mr.configurations,
                mr.licenses,
                filterOutArtificialCallers(mr.callers)
              )
            }
          )
        }
      )
    })

  // #1763/#2030. Caller takes up 97% of space, so we need to shrink it down,
  // but there are semantics associated with some of them.
  def filterOutArtificialCallers(callers: Vector[Caller]): Vector[Caller] =
    if (callers.isEmpty) callers
    else {
      val nonArtificial = callers filter { c =>
        (c.caller.organization != sbtOrgTemp) &&
        (c.caller.organization != fakeCallerOrganization)
      }
      val interProj = (callers find { c =>
        c.caller.organization == sbtOrgTemp
      }).toVector
      interProj ++ nonArtificial
    }

  def fromLite(lite: UpdateReportLite, cachedDescriptor: File): UpdateReport = {
    val stats = UpdateStats(0L, 0L, 0L, false)
    val configReports = lite.configurations map { cr =>
      val details = cr.details
      val modules = details flatMap {
        _.modules filter { mr =>
          !mr.evicted && mr.problem.isEmpty
        }
      }
      ConfigurationReport(ConfigRef(cr.configuration), modules, details)
    }
    UpdateReport(cachedDescriptor, configReports, stats, Map.empty)
  }
}
