/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait UpdateReportLiteFormats { self: sbt.librarymanagement.ConfigurationReportLiteFormats with sbt.librarymanagement.OrganizationArtifactReportFormats with sbt.librarymanagement.ModuleReportFormats with sbt.librarymanagement.ModuleIDFormats with sbt.librarymanagement.ArtifactFormats with sbt.librarymanagement.ConfigRefFormats with sbt.librarymanagement.ChecksumFormats with sjsonnew.BasicJsonProtocol with sbt.librarymanagement.InclExclRuleFormats with sbt.librarymanagement.CrossVersionFormats with sbt.librarymanagement.DisabledFormats with sbt.librarymanagement.BinaryFormats with sbt.librarymanagement.ConstantFormats with sbt.librarymanagement.PatchFormats with sbt.librarymanagement.FullFormats with sbt.librarymanagement.For3Use2_13Formats with sbt.librarymanagement.For2_13Use3Formats with sbt.librarymanagement.CallerFormats =>
implicit lazy val UpdateReportLiteFormat: JsonFormat[sbt.internal.librarymanagement.UpdateReportLite] = new JsonFormat[sbt.internal.librarymanagement.UpdateReportLite] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.librarymanagement.UpdateReportLite = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val configurations = unbuilder.readField[Vector[sbt.internal.librarymanagement.ConfigurationReportLite]]("configurations")
      unbuilder.endObject()
      sbt.internal.librarymanagement.UpdateReportLite(configurations)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.librarymanagement.UpdateReportLite, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("configurations", obj.configurations)
    builder.endObject()
  }
}
}
