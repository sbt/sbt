/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait UpdateReportLiteFormats { self: sbt.librarymanagement.ConfigurationReportLiteFormats with sjsonnew.BasicJsonProtocol =>
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
