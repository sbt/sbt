/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ConfigurationReportLiteFormats { self: sbt.librarymanagement.OrganizationArtifactReportFormats & sbt.librarymanagement.ModuleReportFormats & sbt.librarymanagement.ModuleIDFormats & sbt.librarymanagement.ArtifactFormats & sbt.librarymanagement.ConfigRefFormats & sbt.librarymanagement.ChecksumFormats & sjsonnew.BasicJsonProtocol & sbt.librarymanagement.InclExclRuleFormats & sbt.librarymanagement.CrossVersionFormats & sbt.librarymanagement.DisabledFormats & sbt.librarymanagement.BinaryFormats & sbt.librarymanagement.ConstantFormats & sbt.librarymanagement.PatchFormats & sbt.librarymanagement.FullFormats & sbt.librarymanagement.For3Use2_13Formats & sbt.librarymanagement.For2_13Use3Formats & sbt.librarymanagement.CallerFormats =>
implicit lazy val ConfigurationReportLiteFormat: JsonFormat[sbt.internal.librarymanagement.ConfigurationReportLite] = new JsonFormat[sbt.internal.librarymanagement.ConfigurationReportLite] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.librarymanagement.ConfigurationReportLite = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val configuration = unbuilder.readField[String]("configuration")
      val details = unbuilder.readField[Vector[sbt.librarymanagement.OrganizationArtifactReport]]("details")
      unbuilder.endObject()
      sbt.internal.librarymanagement.ConfigurationReportLite(configuration, details)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.librarymanagement.ConfigurationReportLite, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("configuration", obj.configuration)
    builder.addField("details", obj.details)
    builder.endObject()
  }
}
}
