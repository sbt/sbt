/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ConfigurationReportFormats { self: sbt.librarymanagement.ConfigRefFormats with sbt.librarymanagement.ModuleReportFormats with sbt.librarymanagement.ModuleIDFormats with sbt.librarymanagement.ArtifactFormats with sbt.librarymanagement.ChecksumFormats with sjsonnew.BasicJsonProtocol with sbt.librarymanagement.InclExclRuleFormats with sbt.librarymanagement.CrossVersionFormats with sbt.librarymanagement.DisabledFormats with sbt.librarymanagement.BinaryFormats with sbt.librarymanagement.ConstantFormats with sbt.librarymanagement.PatchFormats with sbt.librarymanagement.FullFormats with sbt.librarymanagement.For3Use2_13Formats with sbt.librarymanagement.For2_13Use3Formats with sbt.librarymanagement.CallerFormats with sbt.librarymanagement.OrganizationArtifactReportFormats =>
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
