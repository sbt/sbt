package sbt

// put under the sbt namespace to access private[sbt] things (the copy method used below)

class ModuleReportOps(val report: sbt.ModuleReport) extends AnyVal {
  def withPublicationDate(publicationDate: Option[java.util.Calendar]): sbt.ModuleReport =
    report.copy(publicationDate = publicationDate.map(_.getTime))
  def withHomepage(homepage: Option[String]): sbt.ModuleReport =
    report.copy(homepage = homepage)
  def withExtraAttributes(extraAttributes: Map[String, String]): sbt.ModuleReport =
    report.copy(extraAttributes = extraAttributes)
  def withConfigurations(configurations: Vector[coursier.SbtCompatibility.ConfigRef]): sbt.ModuleReport =
    report.copy(configurations = configurations.map(_.name))
  def withLicenses(licenses: Vector[(String, Option[String])]): sbt.ModuleReport =
    report.copy(licenses = licenses)
  def withCallers(callers: Vector[sbt.Caller]): sbt.ModuleReport =
    report.copy(callers = callers)
}
