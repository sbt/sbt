/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait OrganizationArtifactReportFormats { self: sbt.librarymanagement.ModuleReportFormats & sbt.librarymanagement.ModuleIDFormats & sbt.librarymanagement.ArtifactFormats & sbt.librarymanagement.ConfigRefFormats & sbt.librarymanagement.ChecksumFormats & sjsonnew.BasicJsonProtocol & sbt.librarymanagement.InclExclRuleFormats & sbt.librarymanagement.CrossVersionFormats & sbt.librarymanagement.DisabledFormats & sbt.librarymanagement.BinaryFormats & sbt.librarymanagement.ConstantFormats & sbt.librarymanagement.PatchFormats & sbt.librarymanagement.FullFormats & sbt.librarymanagement.For3Use2_13Formats & sbt.librarymanagement.For2_13Use3Formats & sbt.librarymanagement.CallerFormats =>
implicit lazy val OrganizationArtifactReportFormat: JsonFormat[sbt.librarymanagement.OrganizationArtifactReport] = new JsonFormat[sbt.librarymanagement.OrganizationArtifactReport] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.OrganizationArtifactReport = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val organization = unbuilder.readField[String]("organization")
      val name = unbuilder.readField[String]("name")
      val modules = unbuilder.readField[Vector[sbt.librarymanagement.ModuleReport]]("modules")
      unbuilder.endObject()
      sbt.librarymanagement.OrganizationArtifactReport(organization, name, modules)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.OrganizationArtifactReport, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("organization", obj.organization)
    builder.addField("name", obj.name)
    builder.addField("modules", obj.modules)
    builder.endObject()
  }
}
}
