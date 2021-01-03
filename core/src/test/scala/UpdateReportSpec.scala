package sbt.librarymanagement

import java.io.File

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class UpdateReportSpec extends AnyFlatSpec with Matchers {
  "UpdateReport.toString" should "have a nice toString" in {
    assert(updateReport.toString === s"""
      |Update report:
      |	Resolve time: 0 ms, Download time: 0 ms, Download size: 0 bytes
      |	compile:
      |	org:name
      |		- 1.0
      |			publicationDate: 1970-01-01T00:00:00Z
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

  lazy val moduleReport = (
    ModuleReport(ModuleID("org", "name", "1.0"), Vector.empty, Vector.empty)
      withPublicationDate Some(epochCalendar)
  )

  lazy val organizationArtifactReport =
    OrganizationArtifactReport("org", "name", Vector(moduleReport))

  val epochCalendar: java.util.Calendar = {
    val utc = java.util.TimeZone getTimeZone "UTC"
    val c = new java.util.GregorianCalendar(utc, java.util.Locale.ENGLISH)
    c setTimeInMillis 0L
    c
  }
}
