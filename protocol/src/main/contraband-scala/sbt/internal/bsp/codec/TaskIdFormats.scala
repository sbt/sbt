/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TaskIdFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val TaskIdFormat: JsonFormat[sbt.internal.bsp.TaskId] = new JsonFormat[sbt.internal.bsp.TaskId] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.TaskId = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val id = unbuilder.readField[String]("id")
      val parents = unbuilder.readField[Vector[String]]("parents")
      unbuilder.endObject()
      sbt.internal.bsp.TaskId(id, parents)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.TaskId, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("id", obj.id)
    builder.addField("parents", obj.parents)
    builder.endObject()
  }
}
}
