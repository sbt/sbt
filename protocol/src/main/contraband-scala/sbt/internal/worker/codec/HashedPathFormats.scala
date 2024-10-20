/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait HashedPathFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val HashedPathFormat: JsonFormat[sbt.internal.worker.HashedPath] = new JsonFormat[sbt.internal.worker.HashedPath] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.worker.HashedPath = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val path = unbuilder.readField[String]("path")
      val digest = unbuilder.readField[String]("digest")
      unbuilder.endObject()
      sbt.internal.worker.HashedPath(path, digest)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.worker.HashedPath, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("path", obj.path)
    builder.addField("digest", obj.digest)
    builder.endObject()
  }
}
}
