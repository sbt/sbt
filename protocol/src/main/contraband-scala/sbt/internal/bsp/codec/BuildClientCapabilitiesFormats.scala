/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait BuildClientCapabilitiesFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val BuildClientCapabilitiesFormat: JsonFormat[sbt.internal.bsp.BuildClientCapabilities] = new JsonFormat[sbt.internal.bsp.BuildClientCapabilities] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.BuildClientCapabilities = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val languageIds = unbuilder.readField[Vector[String]]("languageIds")
      unbuilder.endObject()
      sbt.internal.bsp.BuildClientCapabilities(languageIds)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.BuildClientCapabilities, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("languageIds", obj.languageIds)
    builder.endObject()
  }
}
}
