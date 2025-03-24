/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ConfigurationReportFormats { self: sbt.librarymanagement.ConfigRefFormats & sbt.librarymanagement.ModuleReportFormats & sbt.librarymanagement.ModuleIDFormats & sbt.librarymanagement.ArtifactFormats & sbt.librarymanagement.ChecksumFormats & sjsonnew.BasicJsonProtocol & sbt.librarymanagement.InclExclRuleFormats & sbt.librarymanagement.CrossVersionFormats & sbt.librarymanagement.DisabledFormats & sbt.librarymanagement.BinaryFormats & sbt.librarymanagement.ConstantFormats & sbt.librarymanagement.PatchFormats & sbt.librarymanagement.FullFormats & sbt.librarymanagement.For3Use2_13Formats & sbt.librarymanagement.For2_13Use3Formats & sbt.librarymanagement.CallerFormats & sbt.librarymanagement.OrganizationArtifactReportFormats =>
implicit lazy val ConfigurationReportFormat: JsonFormat[sbt.librarymanagement.ConfigurationReport] = new JsonFormat[sbt.librarymanagement.ConfigurationReport] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.ConfigurationReport = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val configuration = unbuilder.readField[sbt.librarymanagement.ConfigRef]("configuration")
      val modules = unbuilder.readField[Vector[sbt.librarymanagement.ModuleReport]]("modules")
      val details = unbuilder.readField[Vector[sbt.librarymanagement.OrganizationArtifactReport]]("details")
      unbuilder.endObject()
      sbt.librarymanagement.ConfigurationReport(configuration, modules, details)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.ConfigurationReport, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("configuration", obj.configuration)
    builder.addField("modules", obj.modules)
    builder.addField("details", obj.details)
    builder.endObject()
  }
}
}
