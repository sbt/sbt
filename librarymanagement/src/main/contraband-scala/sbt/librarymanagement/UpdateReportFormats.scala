/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait UpdateReportFormats { self: sbt.librarymanagement.ConfigurationReportFormats with sbt.librarymanagement.UpdateStatsFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val UpdateReportFormat: JsonFormat[sbt.librarymanagement.UpdateReport] = new JsonFormat[sbt.librarymanagement.UpdateReport] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.UpdateReport = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val cachedDescriptor = unbuilder.readField[java.io.File]("cachedDescriptor")
      val configurations = unbuilder.readField[Vector[sbt.librarymanagement.ConfigurationReport]]("configurations")
      val stats = unbuilder.readField[sbt.librarymanagement.UpdateStats]("stats")
      val stamps = unbuilder.readField[Map[java.io.File, Long]]("stamps")
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
