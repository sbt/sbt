/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait CleanCacheResultFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val CleanCacheResultFormat: JsonFormat[sbt.internal.bsp.CleanCacheResult] = new JsonFormat[sbt.internal.bsp.CleanCacheResult] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.CleanCacheResult = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val message = unbuilder.readField[Option[String]]("message")
      val cleaned = unbuilder.readField[Boolean]("cleaned")
      unbuilder.endObject()
      sbt.internal.bsp.CleanCacheResult(message, cleaned)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.CleanCacheResult, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("message", obj.message)
    builder.addField("cleaned", obj.cleaned)
    builder.endObject()
  }
}
}
