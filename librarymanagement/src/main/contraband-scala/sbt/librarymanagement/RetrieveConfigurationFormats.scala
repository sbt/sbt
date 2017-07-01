/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait RetrieveConfigurationFormats { self: sbt.librarymanagement.ConfigurationFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val RetrieveConfigurationFormat: JsonFormat[sbt.internal.librarymanagement.RetrieveConfiguration] = new JsonFormat[sbt.internal.librarymanagement.RetrieveConfiguration] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.librarymanagement.RetrieveConfiguration = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val retrieveDirectory = unbuilder.readField[java.io.File]("retrieveDirectory")
      val outputPattern = unbuilder.readField[String]("outputPattern")
      val sync = unbuilder.readField[Boolean]("sync")
      val configurationsToRetrieve = unbuilder.readField[Option[Set[sbt.librarymanagement.Configuration]]]("configurationsToRetrieve")
      unbuilder.endObject()
      sbt.internal.librarymanagement.RetrieveConfiguration(retrieveDirectory, outputPattern, sync, configurationsToRetrieve)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.librarymanagement.RetrieveConfiguration, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("retrieveDirectory", obj.retrieveDirectory)
    builder.addField("outputPattern", obj.outputPattern)
    builder.addField("sync", obj.sync)
    builder.addField("configurationsToRetrieve", obj.configurationsToRetrieve)
    builder.endObject()
  }
}
}
