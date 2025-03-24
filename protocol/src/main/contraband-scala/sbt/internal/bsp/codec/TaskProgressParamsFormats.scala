/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TaskProgressParamsFormats { self: sbt.internal.bsp.codec.TaskIdFormats & sjsonnew.BasicJsonProtocol & sbt.internal.util.codec.JValueFormats =>
implicit lazy val TaskProgressParamsFormat: JsonFormat[sbt.internal.bsp.TaskProgressParams] = new JsonFormat[sbt.internal.bsp.TaskProgressParams] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.TaskProgressParams = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val taskId = unbuilder.readField[sbt.internal.bsp.TaskId]("taskId")
      val eventTime = unbuilder.readField[Option[Long]]("eventTime")
      val message = unbuilder.readField[Option[String]]("message")
      val total = unbuilder.readField[Option[Long]]("total")
      val progress = unbuilder.readField[Option[Long]]("progress")
      val unit = unbuilder.readField[Option[String]]("unit")
      val dataKind = unbuilder.readField[Option[String]]("dataKind")
      val data = unbuilder.readField[Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]]("data")
      unbuilder.endObject()
      sbt.internal.bsp.TaskProgressParams(taskId, eventTime, message, total, progress, unit, dataKind, data)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.TaskProgressParams, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("taskId", obj.taskId)
    builder.addField("eventTime", obj.eventTime)
    builder.addField("message", obj.message)
    builder.addField("total", obj.total)
    builder.addField("progress", obj.progress)
    builder.addField("unit", obj.unit)
    builder.addField("dataKind", obj.dataKind)
    builder.addField("data", obj.data)
    builder.endObject()
  }
}
}
