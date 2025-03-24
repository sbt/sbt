/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TaskStartParamsFormats { self: sbt.internal.bsp.codec.TaskIdFormats & sjsonnew.BasicJsonProtocol & sbt.internal.util.codec.JValueFormats =>
implicit lazy val TaskStartParamsFormat: JsonFormat[sbt.internal.bsp.TaskStartParams] = new JsonFormat[sbt.internal.bsp.TaskStartParams] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.TaskStartParams = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val taskId = unbuilder.readField[sbt.internal.bsp.TaskId]("taskId")
      val eventTime = unbuilder.readField[Option[Long]]("eventTime")
      val message = unbuilder.readField[Option[String]]("message")
      val dataKind = unbuilder.readField[Option[String]]("dataKind")
      val data = unbuilder.readField[Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]]("data")
      unbuilder.endObject()
      sbt.internal.bsp.TaskStartParams(taskId, eventTime, message, dataKind, data)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.TaskStartParams, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("taskId", obj.taskId)
    builder.addField("eventTime", obj.eventTime)
    builder.addField("message", obj.message)
    builder.addField("dataKind", obj.dataKind)
    builder.addField("data", obj.data)
    builder.endObject()
  }
}
}
