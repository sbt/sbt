/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait DependencySourcesResultFormats { self: sbt.internal.bsp.codec.DependencySourcesItemFormats & sbt.internal.bsp.codec.BuildTargetIdentifierFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val DependencySourcesResultFormat: JsonFormat[sbt.internal.bsp.DependencySourcesResult] = new JsonFormat[sbt.internal.bsp.DependencySourcesResult] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.DependencySourcesResult = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val items = unbuilder.readField[Vector[sbt.internal.bsp.DependencySourcesItem]]("items")
      unbuilder.endObject()
      sbt.internal.bsp.DependencySourcesResult(items)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.DependencySourcesResult, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("items", obj.items)
    builder.endObject()
  }
}
}
