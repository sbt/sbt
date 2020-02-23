/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait SourcesResultFormats { self: sbt.internal.bsp.codec.SourcesItemFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val SourcesResultFormat: JsonFormat[sbt.internal.bsp.SourcesResult] = new JsonFormat[sbt.internal.bsp.SourcesResult] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.SourcesResult = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val items = unbuilder.readField[Vector[sbt.internal.bsp.SourcesItem]]("items")
      unbuilder.endObject()
      sbt.internal.bsp.SourcesResult(items)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.SourcesResult, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("items", obj.items)
    builder.endObject()
  }
}
}
