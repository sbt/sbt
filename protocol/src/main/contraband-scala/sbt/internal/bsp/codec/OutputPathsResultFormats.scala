/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait OutputPathsResultFormats { self: sbt.internal.bsp.codec.OutputPathsItemFormats & sbt.internal.bsp.codec.BuildTargetIdentifierFormats & sjsonnew.BasicJsonProtocol & sbt.internal.bsp.codec.OutputPathItemFormats =>
implicit lazy val OutputPathsResultFormat: JsonFormat[sbt.internal.bsp.OutputPathsResult] = new JsonFormat[sbt.internal.bsp.OutputPathsResult] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.OutputPathsResult = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val items = unbuilder.readField[Vector[sbt.internal.bsp.OutputPathsItem]]("items")
      unbuilder.endObject()
      sbt.internal.bsp.OutputPathsResult(items)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.OutputPathsResult, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("items", obj.items)
    builder.endObject()
  }
}
}
