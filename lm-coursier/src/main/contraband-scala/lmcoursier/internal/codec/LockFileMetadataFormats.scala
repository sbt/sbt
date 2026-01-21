/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package lmcoursier.internal.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait LockFileMetadataFormats { self: lmcoursier.internal.codec.InstantFormats & sjsonnew.BasicJsonProtocol =>
given LockFileMetadataFormat: JsonFormat[lmcoursier.internal.LockFileMetadata] = new JsonFormat[lmcoursier.internal.LockFileMetadata] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): lmcoursier.internal.LockFileMetadata = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val sbtVersion = unbuilder.readField[String]("sbtVersion")
      val scalaVersion = unbuilder.readField[Option[String]]("scalaVersion")
      val timestamp = unbuilder.readField[java.time.Instant]("timestamp")
      unbuilder.endObject()
      lmcoursier.internal.LockFileMetadata(sbtVersion, scalaVersion, timestamp)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: lmcoursier.internal.LockFileMetadata, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("sbtVersion", obj.sbtVersion)
    builder.addField("scalaVersion", obj.scalaVersion)
    builder.addField("timestamp", obj.timestamp)
    builder.endObject()
  }
}
}
