/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ExecutionEventFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val ExecutionEventFormat: JsonFormat[sbt.protocol.ExecutionEvent] = new JsonFormat[sbt.protocol.ExecutionEvent] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.ExecutionEvent = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val success = unbuilder.readField[String]("success")
      val commandLine = unbuilder.readField[String]("commandLine")
      unbuilder.endObject()
      sbt.protocol.ExecutionEvent(success, commandLine)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.ExecutionEvent, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("success", obj.success)
    builder.addField("commandLine", obj.commandLine)
    builder.endObject()
  }
}
}
