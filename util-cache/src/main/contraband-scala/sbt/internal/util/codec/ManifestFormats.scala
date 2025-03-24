/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ManifestFormats { self: sbt.internal.util.codec.HashedVirtualFileRefFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val ManifestFormat: JsonFormat[sbt.util.Manifest] = new JsonFormat[sbt.util.Manifest] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.util.Manifest = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val version = unbuilder.readField[String]("version")
      val outputFiles = unbuilder.readField[Vector[xsbti.HashedVirtualFileRef]]("outputFiles")
      unbuilder.endObject()
      sbt.util.Manifest(version, outputFiles)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.util.Manifest, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("version", obj.version)
    builder.addField("outputFiles", obj.outputFiles)
    builder.endObject()
  }
}
}
