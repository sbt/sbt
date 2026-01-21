/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package lmcoursier.internal.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait DependencyLockFormats { self: lmcoursier.internal.codec.ArtifactLockFormats & sjsonnew.BasicJsonProtocol =>
given DependencyLockFormat: JsonFormat[lmcoursier.internal.DependencyLock] = new JsonFormat[lmcoursier.internal.DependencyLock] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): lmcoursier.internal.DependencyLock = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val organization = unbuilder.readField[String]("organization")
      val name = unbuilder.readField[String]("name")
      val version = unbuilder.readField[String]("version")
      val configuration = unbuilder.readField[String]("configuration")
      val classifier = unbuilder.readField[Option[String]]("classifier")
      val tpe = unbuilder.readField[String]("tpe")
      val transitives = unbuilder.readField[Vector[String]]("transitives")
      val artifacts = unbuilder.readField[Vector[lmcoursier.internal.ArtifactLock]]("artifacts")
      unbuilder.endObject()
      lmcoursier.internal.DependencyLock(organization, name, version, configuration, classifier, tpe, transitives, artifacts)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: lmcoursier.internal.DependencyLock, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("organization", obj.organization)
    builder.addField("name", obj.name)
    builder.addField("version", obj.version)
    builder.addField("configuration", obj.configuration)
    builder.addField("classifier", obj.classifier)
    builder.addField("tpe", obj.tpe)
    builder.addField("transitives", obj.transitives)
    builder.addField("artifacts", obj.artifacts)
    builder.endObject()
  }
}
}
