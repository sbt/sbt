/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait FilePathFormats { self: sjsonnew.BasicJsonProtocol =>
given FilePathFormat: JsonFormat[sbt.internal.worker.FilePath] = new JsonFormat[sbt.internal.worker.FilePath] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.worker.FilePath = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val path = unbuilder.readField[java.net.URI]("path")
      val digest = unbuilder.readField[String]("digest")
      unbuilder.endObject()
      sbt.internal.worker.FilePath(path, digest)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.worker.FilePath, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("path", obj.path)
    builder.addField("digest", obj.digest)
    builder.endObject()
  }
}
}
