/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package lmcoursier.internal.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ArtifactLockFormats { self: sjsonnew.BasicJsonProtocol =>
given ArtifactLockFormat: JsonFormat[lmcoursier.internal.ArtifactLock] = new JsonFormat[lmcoursier.internal.ArtifactLock] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): lmcoursier.internal.ArtifactLock = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val url = unbuilder.readField[String]("url")
      val classifier = unbuilder.readField[Option[String]]("classifier")
      val extension = unbuilder.readField[String]("extension")
      val tpe = unbuilder.readField[String]("tpe")
      unbuilder.endObject()
      lmcoursier.internal.ArtifactLock(url, classifier, extension, tpe)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: lmcoursier.internal.ArtifactLock, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("url", obj.url)
    builder.addField("classifier", obj.classifier)
    builder.addField("extension", obj.extension)
    builder.addField("tpe", obj.tpe)
    builder.endObject()
  }
}
}
