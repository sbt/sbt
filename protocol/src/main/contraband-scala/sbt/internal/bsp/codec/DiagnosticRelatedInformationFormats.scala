/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait DiagnosticRelatedInformationFormats { self: sbt.internal.bsp.codec.LocationFormats & sbt.internal.bsp.codec.RangeFormats & sbt.internal.bsp.codec.PositionFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val DiagnosticRelatedInformationFormat: JsonFormat[sbt.internal.bsp.DiagnosticRelatedInformation] = new JsonFormat[sbt.internal.bsp.DiagnosticRelatedInformation] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.DiagnosticRelatedInformation = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val location = unbuilder.readField[sbt.internal.bsp.Location]("location")
      val message = unbuilder.readField[String]("message")
      unbuilder.endObject()
      sbt.internal.bsp.DiagnosticRelatedInformation(location, message)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.DiagnosticRelatedInformation, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("location", obj.location)
    builder.addField("message", obj.message)
    builder.endObject()
  }
}
}
