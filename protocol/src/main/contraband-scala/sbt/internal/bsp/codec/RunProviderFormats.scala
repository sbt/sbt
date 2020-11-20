/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait RunProviderFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val RunProviderFormat: JsonFormat[sbt.internal.bsp.RunProvider] = new JsonFormat[sbt.internal.bsp.RunProvider] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.RunProvider = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val languageIds = unbuilder.readField[Vector[String]]("languageIds")
      unbuilder.endObject()
      sbt.internal.bsp.RunProvider(languageIds)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.RunProvider, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("languageIds", obj.languageIds)
    builder.endObject()
  }
}
}
