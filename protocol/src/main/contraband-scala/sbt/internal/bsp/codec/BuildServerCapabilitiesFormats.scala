/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait BuildServerCapabilitiesFormats { self: sbt.internal.bsp.codec.CompileProviderFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val BuildServerCapabilitiesFormat: JsonFormat[sbt.internal.bsp.BuildServerCapabilities] = new JsonFormat[sbt.internal.bsp.BuildServerCapabilities] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.BuildServerCapabilities = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val compileProvider = unbuilder.readField[Option[sbt.internal.bsp.CompileProvider]]("compileProvider")
      unbuilder.endObject()
      sbt.internal.bsp.BuildServerCapabilities(compileProvider)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.BuildServerCapabilities, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("compileProvider", obj.compileProvider)
    builder.endObject()
  }
}
}
