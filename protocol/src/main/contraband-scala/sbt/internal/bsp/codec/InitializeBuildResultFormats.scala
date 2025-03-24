/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait InitializeBuildResultFormats { self: sbt.internal.bsp.codec.BuildServerCapabilitiesFormats & sbt.internal.bsp.codec.CompileProviderFormats & sjsonnew.BasicJsonProtocol & sbt.internal.bsp.codec.TestProviderFormats & sbt.internal.bsp.codec.RunProviderFormats & sbt.internal.bsp.codec.DebugProviderFormats & sbt.internal.util.codec.JValueFormats =>
implicit lazy val InitializeBuildResultFormat: JsonFormat[sbt.internal.bsp.InitializeBuildResult] = new JsonFormat[sbt.internal.bsp.InitializeBuildResult] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.InitializeBuildResult = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val displayName = unbuilder.readField[String]("displayName")
      val version = unbuilder.readField[String]("version")
      val bspVersion = unbuilder.readField[String]("bspVersion")
      val capabilities = unbuilder.readField[sbt.internal.bsp.BuildServerCapabilities]("capabilities")
      val data = unbuilder.readField[Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]]("data")
      unbuilder.endObject()
      sbt.internal.bsp.InitializeBuildResult(displayName, version, bspVersion, capabilities, data)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.InitializeBuildResult, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("displayName", obj.displayName)
    builder.addField("version", obj.version)
    builder.addField("bspVersion", obj.bspVersion)
    builder.addField("capabilities", obj.capabilities)
    builder.addField("data", obj.data)
    builder.endObject()
  }
}
}
