/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ResourcesItemFormats { self: sbt.internal.bsp.codec.BuildTargetIdentifierFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val ResourcesItemFormat: JsonFormat[sbt.internal.bsp.ResourcesItem] = new JsonFormat[sbt.internal.bsp.ResourcesItem] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.ResourcesItem = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val target = unbuilder.readField[sbt.internal.bsp.BuildTargetIdentifier]("target")
      val resources = unbuilder.readField[Vector[java.net.URI]]("resources")
      unbuilder.endObject()
      sbt.internal.bsp.ResourcesItem(target, resources)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.ResourcesItem, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("target", obj.target)
    builder.addField("resources", obj.resources)
    builder.endObject()
  }
}
}
