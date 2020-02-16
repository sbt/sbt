/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait InitializeBuildParamsFormats { self: sbt.internal.bsp.codec.BuildClientCapabilitiesFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val InitializeBuildParamsFormat: JsonFormat[sbt.internal.bsp.InitializeBuildParams] = new JsonFormat[sbt.internal.bsp.InitializeBuildParams] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.InitializeBuildParams = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val displayName = unbuilder.readField[String]("displayName")
      val version = unbuilder.readField[String]("version")
      val bspVersion = unbuilder.readField[String]("bspVersion")
      val rootUri = unbuilder.readField[java.net.URI]("rootUri")
      val capabilities = unbuilder.readField[sbt.internal.bsp.BuildClientCapabilities]("capabilities")
      unbuilder.endObject()
      sbt.internal.bsp.InitializeBuildParams(displayName, version, bspVersion, rootUri, capabilities)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.InitializeBuildParams, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("displayName", obj.displayName)
    builder.addField("version", obj.version)
    builder.addField("bspVersion", obj.bspVersion)
    builder.addField("rootUri", obj.rootUri)
    builder.addField("capabilities", obj.capabilities)
    builder.endObject()
  }
}
}
