/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package lmcoursier.internal.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait LockFileDataFormats { self: lmcoursier.internal.codec.ConfigurationLockFormats & lmcoursier.internal.codec.DependencyLockFormats & lmcoursier.internal.codec.ArtifactLockFormats & sjsonnew.BasicJsonProtocol & lmcoursier.internal.codec.LockFileMetadataFormats & lmcoursier.internal.codec.InstantFormats =>
given LockFileDataFormat: JsonFormat[lmcoursier.internal.LockFileData] = new JsonFormat[lmcoursier.internal.LockFileData] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): lmcoursier.internal.LockFileData = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val version = unbuilder.readField[String]("version")
      val buildClock = unbuilder.readField[String]("buildClock")
      val configurations = unbuilder.readField[Vector[lmcoursier.internal.ConfigurationLock]]("configurations")
      val metadata = unbuilder.readField[lmcoursier.internal.LockFileMetadata]("metadata")
      unbuilder.endObject()
      lmcoursier.internal.LockFileData(version, buildClock, configurations, metadata)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: lmcoursier.internal.LockFileData, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("version", obj.version)
    builder.addField("buildClock", obj.buildClock)
    builder.addField("configurations", obj.configurations)
    builder.addField("metadata", obj.metadata)
    builder.endObject()
  }
}
}
