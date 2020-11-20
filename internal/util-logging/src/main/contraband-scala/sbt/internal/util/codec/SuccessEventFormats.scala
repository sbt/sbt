/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait SuccessEventFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val SuccessEventFormat: JsonFormat[sbt.internal.util.SuccessEvent] = new JsonFormat[sbt.internal.util.SuccessEvent] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.util.SuccessEvent = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val message = unbuilder.readField[String]("message")
      unbuilder.endObject()
      sbt.internal.util.SuccessEvent(message)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.util.SuccessEvent, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("message", obj.message)
    builder.endObject()
  }
}
}
