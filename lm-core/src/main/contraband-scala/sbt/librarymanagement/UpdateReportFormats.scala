/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait UpdateReportFormats { self: sbt.librarymanagement.ConfigurationReportFormats & sbt.librarymanagement.ConfigRefFormats & sbt.librarymanagement.ModuleReportFormats & sbt.librarymanagement.ModuleIDFormats & sbt.librarymanagement.ArtifactFormats & sbt.librarymanagement.ChecksumFormats & sjsonnew.BasicJsonProtocol & sbt.librarymanagement.InclExclRuleFormats & sbt.librarymanagement.CrossVersionFormats & sbt.librarymanagement.DisabledFormats & sbt.librarymanagement.BinaryFormats & sbt.librarymanagement.ConstantFormats & sbt.librarymanagement.PatchFormats & sbt.librarymanagement.FullFormats & sbt.librarymanagement.For3Use2_13Formats & sbt.librarymanagement.For2_13Use3Formats & sbt.librarymanagement.CallerFormats & sbt.librarymanagement.OrganizationArtifactReportFormats & sbt.librarymanagement.UpdateStatsFormats =>
implicit lazy val UpdateReportFormat: JsonFormat[sbt.librarymanagement.UpdateReport] = new JsonFormat[sbt.librarymanagement.UpdateReport] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.UpdateReport = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val cachedDescriptor = unbuilder.readField[java.io.File]("cachedDescriptor")
      val configurations = unbuilder.readField[Vector[sbt.librarymanagement.ConfigurationReport]]("configurations")
      val stats = unbuilder.readField[sbt.librarymanagement.UpdateStats]("stats")
      val stamps = unbuilder.readField[Map[String, Long]]("stamps")
      unbuilder.endObject()
      sbt.librarymanagement.UpdateReport(cachedDescriptor, configurations, stats, stamps)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.UpdateReport, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("cachedDescriptor", obj.cachedDescriptor)
    builder.addField("configurations", obj.configurations)
    builder.addField("stats", obj.stats)
    builder.addField("stamps", obj.stamps)
    builder.endObject()
  }
}
}
