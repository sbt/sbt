/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait OutputPathsItemFormats { self: sbt.internal.bsp.codec.BuildTargetIdentifierFormats & sjsonnew.BasicJsonProtocol & sbt.internal.bsp.codec.OutputPathItemFormats =>
implicit lazy val OutputPathsItemFormat: JsonFormat[sbt.internal.bsp.OutputPathsItem] = new JsonFormat[sbt.internal.bsp.OutputPathsItem] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.OutputPathsItem = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val target = unbuilder.readField[sbt.internal.bsp.BuildTargetIdentifier]("target")
      val outputPaths = unbuilder.readField[Vector[sbt.internal.bsp.OutputPathItem]]("outputPaths")
      unbuilder.endObject()
      sbt.internal.bsp.OutputPathsItem(target, outputPaths)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.OutputPathsItem, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("target", obj.target)
    builder.addField("outputPaths", obj.outputPaths)
    builder.endObject()
  }
}
}
