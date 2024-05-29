/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait PublishConfigurationFormats { self: sbt.librarymanagement.ConfigRefFormats with sbt.librarymanagement.ArtifactFormats with sbt.librarymanagement.UpdateLoggingFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val PublishConfigurationFormat: JsonFormat[sbt.librarymanagement.PublishConfiguration] = new JsonFormat[sbt.librarymanagement.PublishConfiguration] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.PublishConfiguration = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val publishMavenStyle = unbuilder.readField[Boolean]("publishMavenStyle")
      val deliverIvyPattern = unbuilder.readField[Option[String]]("deliverIvyPattern")
      val status = unbuilder.readField[Option[String]]("status")
      val configurations = unbuilder.readField[Option[scala.Vector[sbt.librarymanagement.ConfigRef]]]("configurations")
      val resolverName = unbuilder.readField[Option[String]]("resolverName")
      val artifacts = unbuilder.readField[Vector[scala.Tuple2[sbt.librarymanagement.Artifact, java.io.File]]]("artifacts")
      val checksums = unbuilder.readField[scala.Vector[String]]("checksums")
      val logging = unbuilder.readField[Option[sbt.librarymanagement.UpdateLogging]]("logging")
      val overwrite = unbuilder.readField[Boolean]("overwrite")
      unbuilder.endObject()
      sbt.librarymanagement.PublishConfiguration(publishMavenStyle, deliverIvyPattern, status, configurations, resolverName, artifacts, checksums, logging, overwrite)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.PublishConfiguration, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("publishMavenStyle", obj.publishMavenStyle)
    builder.addField("deliverIvyPattern", obj.deliverIvyPattern)
    builder.addField("status", obj.status)
    builder.addField("configurations", obj.configurations)
    builder.addField("resolverName", obj.resolverName)
    builder.addField("artifacts", obj.artifacts)
    builder.addField("checksums", obj.checksums)
    builder.addField("logging", obj.logging)
    builder.addField("overwrite", obj.overwrite)
    builder.endObject()
  }
}
}
