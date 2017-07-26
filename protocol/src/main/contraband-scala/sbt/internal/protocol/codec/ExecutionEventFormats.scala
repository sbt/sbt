/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ExecutionEventFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val ExecutionEventFormat: JsonFormat[sbt.internal.protocol.ExecutionEvent] = new JsonFormat[sbt.internal.protocol.ExecutionEvent] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.protocol.ExecutionEvent = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val success = unbuilder.readField[String]("success")
      val commandLine = unbuilder.readField[String]("commandLine")
      unbuilder.endObject()
      sbt.internal.protocol.ExecutionEvent(success, commandLine)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.protocol.ExecutionEvent, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("success", obj.success)
    builder.addField("commandLine", obj.commandLine)
    builder.endObject()
  }
}
}
