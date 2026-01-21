/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package lmcoursier.internal.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ConfigurationLockFormats { self: lmcoursier.internal.codec.DependencyLockFormats & lmcoursier.internal.codec.ArtifactLockFormats & sjsonnew.BasicJsonProtocol =>
given ConfigurationLockFormat: JsonFormat[lmcoursier.internal.ConfigurationLock] = new JsonFormat[lmcoursier.internal.ConfigurationLock] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): lmcoursier.internal.ConfigurationLock = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val name = unbuilder.readField[String]("name")
      val dependencies = unbuilder.readField[Vector[lmcoursier.internal.DependencyLock]]("dependencies")
      unbuilder.endObject()
      lmcoursier.internal.ConfigurationLock(name, dependencies)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: lmcoursier.internal.ConfigurationLock, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("name", obj.name)
    builder.addField("dependencies", obj.dependencies)
    builder.endObject()
  }
}
}
