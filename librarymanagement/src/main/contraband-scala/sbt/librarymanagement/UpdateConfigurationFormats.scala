/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait UpdateConfigurationFormats { self: sbt.librarymanagement.RetrieveConfigurationFormats with sbt.librarymanagement.UpdateLoggingFormats with sbt.librarymanagement.ArtifactTypeFilterFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val UpdateConfigurationFormat: JsonFormat[sbt.librarymanagement.UpdateConfiguration] = new JsonFormat[sbt.librarymanagement.UpdateConfiguration] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.UpdateConfiguration = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val retrieve = unbuilder.readField[Option[sbt.internal.librarymanagement.RetrieveConfiguration]]("retrieve")
      val missingOk = unbuilder.readField[Boolean]("missingOk")
      val logging = unbuilder.readField[sbt.librarymanagement.UpdateLogging]("logging")
      val artifactFilter = unbuilder.readField[sbt.librarymanagement.ArtifactTypeFilter]("artifactFilter")
      val offline = unbuilder.readField[Boolean]("offline")
      val frozen = unbuilder.readField[Boolean]("frozen")
      unbuilder.endObject()
      sbt.librarymanagement.UpdateConfiguration(retrieve, missingOk, logging, artifactFilter, offline, frozen)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.UpdateConfiguration, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("retrieve", obj.retrieve)
    builder.addField("missingOk", obj.missingOk)
    builder.addField("logging", obj.logging)
    builder.addField("artifactFilter", obj.artifactFilter)
    builder.addField("offline", obj.offline)
    builder.addField("frozen", obj.frozen)
    builder.endObject()
  }
}
}
