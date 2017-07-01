/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ConfigurationReportLiteFormats { self: sbt.librarymanagement.OrganizationArtifactReportFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val ConfigurationReportLiteFormat: JsonFormat[sbt.internal.librarymanagement.ConfigurationReportLite] = new JsonFormat[sbt.internal.librarymanagement.ConfigurationReportLite] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.librarymanagement.ConfigurationReportLite = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
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
