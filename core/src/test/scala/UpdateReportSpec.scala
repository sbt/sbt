package sbt.librarymanagement

import java.io.File

import org.scalatest._

class UpdateReportSpec extends FlatSpec with Matchers {
  "UpdateReport.toString" should "have a nice toString" in {
    assert(updateReport.toString === s"""
      |Update report:
      |	Resolve time: 0 ms, Download time: 0 ms, Download size: 0 bytes
      |	compile:
      |	org:name
      |		- 1.0
      |			evicted: false
      |
      |""".stripMargin.drop(1))
  }

  lazy val updateReport =
    UpdateReport(
      new File("cachedDescriptor.data"),
      Vector(configurationReport),
      UpdateStats(0, 0, 0, false),
      Map.empty
    )

  lazy val configurationReport =
    ConfigurationReport(
      ConfigRef("compile"),
      Vector(moduleReport),
      Vector(organizationArtifactReport)
    )

  lazy val moduleReport =
    ModuleReport(ModuleID("org", "name", "1.0"), Vector.empty, Vector.empty)

  lazy val organizationArtifactReport =
    OrganizationArtifactReport("org", "name", Vector(moduleReport))

}
