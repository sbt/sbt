/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait JavacOptionsResultFormats { self: sbt.internal.bsp.codec.JavacOptionsItemFormats & sbt.internal.bsp.codec.BuildTargetIdentifierFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val JavacOptionsResultFormat: JsonFormat[sbt.internal.bsp.JavacOptionsResult] = new JsonFormat[sbt.internal.bsp.JavacOptionsResult] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.JavacOptionsResult = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val items = unbuilder.readField[Vector[sbt.internal.bsp.JavacOptionsItem]]("items")
      unbuilder.endObject()
      sbt.internal.bsp.JavacOptionsResult(items)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.JavacOptionsResult, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("items", obj.items)
    builder.endObject()
  }
}
}
