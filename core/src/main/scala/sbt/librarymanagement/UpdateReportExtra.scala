/* sbt -- Simple Build Tool
 * Copyright 2011  Mark Harrah
 */
package sbt.librarymanagement

import java.io.File
import java.{ util => ju }

private[librarymanagement] abstract class ConfigurationReportExtra {
  def configuration: ConfigRef
  def modules: Vector[ModuleReport]
  def details: Vector[OrganizationArtifactReport]

  /** a sequence of evicted modules */
  def evicted: Seq[ModuleID] =
    details flatMap (_.modules) filter (_.evicted) map (_.module)

  /**
   * All resolved modules for this configuration.
   * For a given organization and module name, there is only one revision/`ModuleID` in this sequence.
   */
  def allModules: Seq[ModuleID] = modules map addConfiguration
  private[this] def addConfiguration(mr: ModuleReport): ModuleID = {
    val module = mr.module
    if (module.configurations.isEmpty) {
      val conf = mr.configurations map (c => s"$configuration->$c") mkString ";"
      module.withConfigurations(Some(conf))
    } else module
  }

  def retrieve(f: (ConfigRef, ModuleID, Artifact, File) => File): ConfigurationReport =
    ConfigurationReport(
      configuration,
      modules map {
        _.retrieve((mid, art, file) => f(configuration, mid, art, file))
      },
      details
    )
}

private[librarymanagement] abstract class ModuleReportExtra {
  def module: ModuleID
  def artifacts: Vector[(Artifact, File)]
  def missingArtifacts: Vector[Artifact]
  def status: Option[String]
  def publicationDate: Option[ju.Calendar]
  def resolver: Option[String]
  def artifactResolver: Option[String]
  def evicted: Boolean
  def evictedData: Option[String]
  def evictedReason: Option[String]
  def problem: Option[String]
  def homepage: Option[String]
  def extraAttributes: Map[String, String]
  def isDefault: Option[Boolean]
  def branch: Option[String]
  def configurations: Vector[ConfigRef]
  def licenses: Vector[(String, Option[String])]
  def callers: Vector[Caller]

  def withArtifacts(artifacts: Vector[(Artifact, File)]): ModuleReport

  protected[this] def arts: Vector[String] =
    artifacts.map(_.toString) ++ missingArtifacts.map(art => "(MISSING) " + art)

  def detailReport: String =
    s"\t\t- ${module.revision}\n" +
      (if (arts.size <= 1) "" else arts.mkString("\t\t\t", "\n\t\t\t", "\n")) +
      reportStr("status", status) +
      reportStr("publicationDate", publicationDate map calendarToString) +
      reportStr("resolver", resolver) +
      reportStr("artifactResolver", artifactResolver) +
      reportStr("evicted", Some(evicted.toString)) +
      reportStr("evictedData", evictedData) +
      reportStr("evictedReason", evictedReason) +
      reportStr("problem", problem) +
      reportStr("homepage", homepage) +
      reportStr(
        "extraAttributes",
        if (extraAttributes.isEmpty) None
        else {
          Some(extraAttributes.toString)
        }
      ) +
      reportStr("isDefault", isDefault map { _.toString }) +
      reportStr("branch", branch) +
      reportStr(
        "configurations",
        if (configurations.isEmpty) None
        else {
          Some(configurations.mkString(", "))
        }
      ) +
      reportStr(
        "licenses",
        if (licenses.isEmpty) None
        else {
          Some(licenses.mkString(", "))
        }
      ) +
      reportStr(
        "callers",
        if (callers.isEmpty) None
        else {
          Some(callers.mkString(", "))
        }
      )
  private[sbt] def reportStr(key: String, value: Option[String]): String =
    value map { x =>
      s"\t\t\t$key: $x\n"
    } getOrElse ""

  private[this] def calendarToString(c: ju.Calendar): String = {
    import sjsonnew._, BasicJsonProtocol._
    implicitly[IsoString[ju.Calendar]] to c
  }

  def retrieve(f: (ModuleID, Artifact, File) => File): ModuleReport =
    withArtifacts(artifacts.map { case (art, file) => (art, f(module, art, file)) })
}

private[librarymanagement] abstract class UpdateReportExtra {
  def cachedDescriptor: File
  def configurations: Vector[ConfigurationReport]
  def stats: UpdateStats
  private[sbt] def stamps: Map[String, Long]

  private[sbt] def moduleKey(m: ModuleID) = (m.organization, m.name, m.revision)

  /** All resolved modules in all configurations. */
  def allModules: Vector[ModuleID] = {
    configurations.flatMap(_.allModules).groupBy(moduleKey).toVector map { case (_, v) =>
      v reduceLeft { (agg, x) =>
        agg.withConfigurations(
          (agg.configurations, x.configurations) match {
            case (None, _)            => x.configurations
            case (Some(ac), None)     => Some(ac)
            case (Some(ac), Some(xc)) => Some(s"$ac;$xc")
          }
        )
      }
    }
  }

  def allModuleReports: Vector[ModuleReport] = {
    configurations.flatMap(_.modules).groupBy(mR => moduleKey(mR.module)).toVector map {
      case (_, v) =>
        v reduceLeft { (agg, x) =>
          agg.withConfigurations(
            (agg.configurations, x.configurations) match {
              case (v, _) if v.isEmpty  => x.configurations
              case (ac, v) if v.isEmpty => ac
              case (ac, xc)             => ac ++ xc
            }
          )
        }
    }
  }

  def retrieve(f: (ConfigRef, ModuleID, Artifact, File) => File): UpdateReport =
    UpdateReport(cachedDescriptor, configurations map { _ retrieve f }, stats, stamps)

  /** Gets the report for the given configuration, or `None` if the configuration was not resolved. */
  def configuration(s: ConfigRef) = configurations.find(_.configuration == s)

  /** Gets the names of all resolved configurations.  This `UpdateReport` contains one `ConfigurationReport` for each configuration in this list. */
  def allConfigurations: Vector[ConfigRef] = configurations.map(_.configuration)
}
