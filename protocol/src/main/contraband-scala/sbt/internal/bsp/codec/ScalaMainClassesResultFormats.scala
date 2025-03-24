/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ScalaMainClassesResultFormats { self: sbt.internal.bsp.codec.ScalaMainClassesItemFormats & sbt.internal.bsp.codec.BuildTargetIdentifierFormats & sjsonnew.BasicJsonProtocol & sbt.internal.bsp.codec.ScalaMainClassFormats =>
implicit lazy val ScalaMainClassesResultFormat: JsonFormat[sbt.internal.bsp.ScalaMainClassesResult] = new JsonFormat[sbt.internal.bsp.ScalaMainClassesResult] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.ScalaMainClassesResult = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val items = unbuilder.readField[Vector[sbt.internal.bsp.ScalaMainClassesItem]]("items")
      val originId = unbuilder.readField[Option[String]]("originId")
      unbuilder.endObject()
      sbt.internal.bsp.ScalaMainClassesResult(items, originId)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.ScalaMainClassesResult, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("items", obj.items)
    builder.addField("originId", obj.originId)
    builder.endObject()
  }
}
}
