/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait CancelRequestParamsFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val CancelRequestParamsFormat: JsonFormat[sbt.internal.langserver.CancelRequestParams] = new JsonFormat[sbt.internal.langserver.CancelRequestParams] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.CancelRequestParams = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val id = unbuilder.readField[String]("id")
      unbuilder.endObject()
      sbt.internal.langserver.CancelRequestParams(id)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.CancelRequestParams, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("id", obj.id)
    builder.endObject()
  }
}
}
