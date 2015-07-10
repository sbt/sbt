package sbt

import java.io.File
import java.net.URL
import org.apache.ivy.core
import core.module.descriptor.ModuleDescriptor
import sbt.serialization._
import java.net.{ URLEncoder, URLDecoder }

private[sbt] object JsonUtil {
  def sbtOrgTemp = "org.scala-sbt.temp"
  def fakeCallerOrganization = "org.scala-sbt.temp-callers"

  def parseUpdateReport(md: ModuleDescriptor, path: File, cachedDescriptor: File, log: Logger): UpdateReport =
    {
      try {
        val lite = fromJsonFile[UpdateReportLite](path).get
        fromLite(lite, cachedDescriptor)
      } catch {
        case e: Throwable =>
          log.error("Unable to parse mini graph: " + path.toString)
          throw e
      }
    }
  def writeUpdateReport(ur: UpdateReport, graphPath: File): Unit =
    {
      IO.createDirectory(graphPath.getParentFile)
      toJsonFile(toLite(ur), graphPath)
    }
  def toLite(ur: UpdateReport): UpdateReportLite =
    UpdateReportLite(ur.configurations map { cr =>
      ConfigurationReportLite(cr.configuration, cr.details map { oar =>
        new OrganizationArtifactReport(oar.organization, oar.name, oar.modules map { mr =>
          new ModuleReport(
            mr.module, mr.artifacts, mr.missingArtifacts, mr.status,
            mr.publicationDate, mr.resolver, mr.artifactResolver,
            mr.evicted, mr.evictedData, mr.evictedReason,
            mr.problem, mr.homepage, mr.extraAttributes,
            mr.isDefault, mr.branch, mr.configurations, mr.licenses,
            filterOutArtificialCallers(mr.callers))
        })
      })
    })
  // #1763/#2030. Caller takes up 97% of space, so we need to shrink it down,
  // but there are semantics associated with some of them.
  def filterOutArtificialCallers(callers: Seq[Caller]): Seq[Caller] =
    if (callers.isEmpty) callers
    else {
      val nonArtificial = callers filter { c =>
        (c.caller.organization != sbtOrgTemp) &&
          (c.caller.organization != fakeCallerOrganization)
      }
      val interProj = (callers filter { c =>
        (c.caller.organization == sbtOrgTemp)
      }).headOption.toList
      interProj ::: nonArtificial.toList
    }

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
}

private[sbt] case class UpdateReportLite(configurations: Seq[ConfigurationReportLite])
private[sbt] object UpdateReportLite {
  implicit val pickler: Pickler[UpdateReportLite] with Unpickler[UpdateReportLite] = PicklerUnpickler.generate[UpdateReportLite]
}

private[sbt] case class ConfigurationReportLite(configuration: String, details: Seq[OrganizationArtifactReport])
private[sbt] object ConfigurationReportLite {
  implicit val pickler: Pickler[ConfigurationReportLite] with Unpickler[ConfigurationReportLite] = PicklerUnpickler.generate[ConfigurationReportLite]
}
