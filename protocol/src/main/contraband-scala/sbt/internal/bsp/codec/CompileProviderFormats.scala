/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait CompileProviderFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val CompileProviderFormat: JsonFormat[sbt.internal.bsp.CompileProvider] = new JsonFormat[sbt.internal.bsp.CompileProvider] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.CompileProvider = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val languageIds = unbuilder.readField[Vector[String]]("languageIds")
      unbuilder.endObject()
      sbt.internal.bsp.CompileProvider(languageIds)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.CompileProvider, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("languageIds", obj.languageIds)
    builder.endObject()
  }
}
}
