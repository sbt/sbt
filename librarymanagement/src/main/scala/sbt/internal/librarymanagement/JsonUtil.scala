package sbt.internal.librarymanagement

import java.io.File
import org.apache.ivy.core
import core.module.descriptor.ModuleDescriptor
import sbt.util.Logger
import sbt.internal.util.CacheStore
import sbt.librarymanagement._
import sbt.librarymanagement.LibraryManagementCodec._
import JsonUtil._

private[sbt] object JsonUtil {
  def sbtOrgTemp = "org.scala-sbt.temp"
  def fakeCallerOrganization = "org.scala-sbt.temp-callers"
}

private[sbt] class JsonUtil(fileToStore: File => CacheStore) {
  def parseUpdateReport(md: ModuleDescriptor, path: File, cachedDescriptor: File, log: Logger): UpdateReport =
    {
      try {
        val lite = fileToStore(path).read[UpdateReportLite]
        fromLite(lite, cachedDescriptor)
      } catch {
        case e: Throwable =>
          log.error("Unable to parse mini graph: " + path.toString)
          throw e
      }
    }
  def writeUpdateReport(ur: UpdateReport, graphPath: File): Unit =
    {
      sbt.io.IO.createDirectory(graphPath.getParentFile)
      fileToStore(graphPath).write(toLite(ur))
    }
  def toLite(ur: UpdateReport): UpdateReportLite =
    UpdateReportLite(ur.configurations map { cr =>
      ConfigurationReportLite(cr.configuration, cr.details map { oar =>
        OrganizationArtifactReport(oar.organization, oar.name, oar.modules map { mr =>
          ModuleReport(
            mr.module, mr.artifacts, mr.missingArtifacts, mr.status,
            mr.publicationDate, mr.resolver, mr.artifactResolver,
            mr.evicted, mr.evictedData, mr.evictedReason,
            mr.problem, mr.homepage, mr.extraAttributes,
            mr.isDefault, mr.branch, mr.configurations, mr.licenses,
            filterOutArtificialCallers(mr.callers)
          )
        })
      })
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

  def fromLite(lite: UpdateReportLite, cachedDescriptor: File): UpdateReport =
    {
      val stats = UpdateStats(0L, 0L, 0L, false)
      val configReports = lite.configurations map { cr =>
        val details = cr.details
        val modules = details flatMap {
          _.modules filter { mr =>
            !mr.evicted && mr.problem.isEmpty
          }
        }
        ConfigurationReport(cr.configuration, modules, details)
      }
      UpdateReport(cachedDescriptor, configReports, stats, Map.empty)
    }
}
