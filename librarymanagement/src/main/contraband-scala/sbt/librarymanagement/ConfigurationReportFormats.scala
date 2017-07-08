/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ConfigurationReportFormats { self: sbt.librarymanagement.ConfigRefFormats with sbt.librarymanagement.ModuleReportFormats with sbt.librarymanagement.OrganizationArtifactReportFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val ConfigurationReportFormat: JsonFormat[sbt.librarymanagement.ConfigurationReport] = new JsonFormat[sbt.librarymanagement.ConfigurationReport] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.ConfigurationReport = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
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
