/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ScalaMainClassesItemFormats { self: sbt.internal.bsp.codec.BuildTargetIdentifierFormats & sjsonnew.BasicJsonProtocol & sbt.internal.bsp.codec.ScalaMainClassFormats =>
implicit lazy val ScalaMainClassesItemFormat: JsonFormat[sbt.internal.bsp.ScalaMainClassesItem] = new JsonFormat[sbt.internal.bsp.ScalaMainClassesItem] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.ScalaMainClassesItem = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val target = unbuilder.readField[sbt.internal.bsp.BuildTargetIdentifier]("target")
      val classes = unbuilder.readField[Vector[sbt.internal.bsp.ScalaMainClass]]("classes")
      unbuilder.endObject()
      sbt.internal.bsp.ScalaMainClassesItem(target, classes)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.ScalaMainClassesItem, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("target", obj.target)
    builder.addField("classes", obj.classes)
    builder.endObject()
  }
}
}
