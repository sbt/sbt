/* sbt -- Simple Build Tool
 * Copyright 2011  Mark Harrah
 */
package sbt.librarymanagement

import java.io.File
import java.{ util => ju }

abstract class ConfigurationReportExtra {
  def configuration: String
  def modules: Vector[ModuleReport]
  def details: Vector[OrganizationArtifactReport]

  /** a sequence of evicted modules */
  def evicted: Seq[ModuleID] =
    details flatMap (_.modules) filter (_.evicted) map (_.module)

  override def toString = s"\t$configuration:\n" +
    (if (details.isEmpty) modules.mkString + details.flatMap(_.modules).filter(_.evicted).map("\t\t(EVICTED) " + _ + "\n").mkString
    else details.mkString)

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

  def retrieve(f: (String, ModuleID, Artifact, File) => File): ConfigurationReport =
    new ConfigurationReport(configuration, modules map { _.retrieve((mid, art, file) => f(configuration, mid, art, file)) }, details)
}

abstract class ModuleReportExtra {
  def module: ModuleID
  def artifacts: Vector[(Artifact, File)]
  def missingArtifacts: Vector[Artifact]
  def status: Option[String]
  def publicationDate: Option[ju.Date]
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
  def configurations: Vector[String]
  def licenses: Vector[(String, Option[String])]
  def callers: Vector[Caller]

  protected[this] def arts: Vector[String] = artifacts.map(_.toString) ++ missingArtifacts.map(art => "(MISSING) " + art)

  def detailReport: String =
    s"\t\t- ${module.revision}\n" +
      (if (arts.size <= 1) "" else arts.mkString("\t\t\t", "\n\t\t\t", "\n")) +
      reportStr("status", status) +
      reportStr("publicationDate", publicationDate map { _.toString }) +
      reportStr("resolver", resolver) +
      reportStr("artifactResolver", artifactResolver) +
      reportStr("evicted", Some(evicted.toString)) +
      reportStr("evictedData", evictedData) +
      reportStr("evictedReason", evictedReason) +
      reportStr("problem", problem) +
      reportStr("homepage", homepage) +
      reportStr(
        "textraAttributes",
        if (extraAttributes.isEmpty) None
        else { Some(extraAttributes.toString) }
      ) +
        reportStr("isDefault", isDefault map { _.toString }) +
        reportStr("branch", branch) +
        reportStr(
          "configurations",
          if (configurations.isEmpty) None
          else { Some(configurations.mkString(", ")) }
        ) +
          reportStr(
            "licenses",
            if (licenses.isEmpty) None
            else { Some(licenses.mkString(", ")) }
          ) +
            reportStr(
              "callers",
              if (callers.isEmpty) None
              else { Some(callers.mkString(", ")) }
            )
  private[sbt] def reportStr(key: String, value: Option[String]): String =
    value map { x => s"\t\t\t$key: $x\n" } getOrElse ""

  def retrieve(f: (ModuleID, Artifact, File) => File): ModuleReport =
    copy(artifacts = artifacts.map { case (art, file) => (art, f(module, art, file)) })

  protected[this] def copy(
    module: ModuleID = module,
    artifacts: Vector[(Artifact, File)] = artifacts,
    missingArtifacts: Vector[Artifact] = missingArtifacts,
    status: Option[String] = status,
    publicationDate: Option[ju.Date] = publicationDate,
    resolver: Option[String] = resolver,
    artifactResolver: Option[String] = artifactResolver,
    evicted: Boolean = evicted,
    evictedData: Option[String] = evictedData,
    evictedReason: Option[String] = evictedReason,
    problem: Option[String] = problem,
    homepage: Option[String] = homepage,
    extraAttributes: Map[String, String] = extraAttributes,
    isDefault: Option[Boolean] = isDefault,
    branch: Option[String] = branch,
    configurations: Vector[String] = configurations,
    licenses: Vector[(String, Option[String])] = licenses,
    callers: Vector[Caller] = callers
  ): ModuleReport
}

abstract class UpdateReportExtra {
  def cachedDescriptor: File
  def configurations: Vector[ConfigurationReport]
  def stats: UpdateStats
  private[sbt] def stamps: Map[File, Long]

  /** All resolved modules in all configurations. */
  def allModules: Vector[ModuleID] =
    {
      val key = (m: ModuleID) => (m.organization, m.name, m.revision)
      configurations.flatMap(_.allModules).groupBy(key).toVector map {
        case (k, v) =>
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

  def retrieve(f: (String, ModuleID, Artifact, File) => File): UpdateReport =
    new UpdateReport(cachedDescriptor, configurations map { _ retrieve f }, stats, stamps)

  /** Gets the report for the given configuration, or `None` if the configuration was not resolved.*/
  def configuration(s: String) = configurations.find(_.configuration == s)

  /** Gets the names of all resolved configurations.  This `UpdateReport` contains one `ConfigurationReport` for each configuration in this list. */
  def allConfigurations: Seq[String] = configurations.map(_.configuration)
}
