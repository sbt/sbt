/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ExcludesResultFormats { self: sbt.internal.bsp.codec.ExcludesItemFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val ExcludesResultFormat: JsonFormat[sbt.internal.bsp.ExcludesResult] = new JsonFormat[sbt.internal.bsp.ExcludesResult] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.ExcludesResult = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val items = unbuilder.readField[Vector[sbt.internal.bsp.ExcludesItem]]("items")
      unbuilder.endObject()
      sbt.internal.bsp.ExcludesResult(items)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.ExcludesResult, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("items", obj.items)
    builder.endObject()
  }
}
}
