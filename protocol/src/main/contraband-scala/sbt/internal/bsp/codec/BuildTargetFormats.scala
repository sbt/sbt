/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait BuildTargetFormats { self: sbt.internal.bsp.codec.BuildTargetIdentifierFormats & sjsonnew.BasicJsonProtocol & sbt.internal.bsp.codec.BuildTargetCapabilitiesFormats & sbt.internal.util.codec.JValueFormats =>
implicit lazy val BuildTargetFormat: JsonFormat[sbt.internal.bsp.BuildTarget] = new JsonFormat[sbt.internal.bsp.BuildTarget] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.BuildTarget = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val id = unbuilder.readField[sbt.internal.bsp.BuildTargetIdentifier]("id")
      val displayName = unbuilder.readField[Option[String]]("displayName")
      val baseDirectory = unbuilder.readField[Option[java.net.URI]]("baseDirectory")
      val tags = unbuilder.readField[Vector[String]]("tags")
      val capabilities = unbuilder.readField[sbt.internal.bsp.BuildTargetCapabilities]("capabilities")
      val languageIds = unbuilder.readField[Vector[String]]("languageIds")
      val dependencies = unbuilder.readField[Vector[sbt.internal.bsp.BuildTargetIdentifier]]("dependencies")
      val dataKind = unbuilder.readField[Option[String]]("dataKind")
      val data = unbuilder.readField[Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]]("data")
      unbuilder.endObject()
      sbt.internal.bsp.BuildTarget(id, displayName, baseDirectory, tags, capabilities, languageIds, dependencies, dataKind, data)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.BuildTarget, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("id", obj.id)
    builder.addField("displayName", obj.displayName)
    builder.addField("baseDirectory", obj.baseDirectory)
    builder.addField("tags", obj.tags)
    builder.addField("capabilities", obj.capabilities)
    builder.addField("languageIds", obj.languageIds)
    builder.addField("dependencies", obj.dependencies)
    builder.addField("dataKind", obj.dataKind)
    builder.addField("data", obj.data)
    builder.endObject()
  }
}
}
