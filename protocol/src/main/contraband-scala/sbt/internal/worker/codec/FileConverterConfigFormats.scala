/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait FileConverterConfigFormats { self: sbt.internal.worker.codec.StringURIFormats & sjsonnew.BasicJsonProtocol =>
given FileConverterConfigFormat: JsonFormat[sbt.internal.worker.FileConverterConfig] = new JsonFormat[sbt.internal.worker.FileConverterConfig] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.worker.FileConverterConfig = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val rootPaths = unbuilder.readField[Vector[sbt.internal.worker.StringURI]]("rootPaths")
      unbuilder.endObject()
      sbt.internal.worker.FileConverterConfig(rootPaths)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.worker.FileConverterConfig, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("rootPaths", obj.rootPaths)
    builder.endObject()
  }
}
}
