/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait RetrieveConfigurationFormats { self: sbt.librarymanagement.ConfigRefFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val RetrieveConfigurationFormat: JsonFormat[sbt.librarymanagement.RetrieveConfiguration] = new JsonFormat[sbt.librarymanagement.RetrieveConfiguration] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.RetrieveConfiguration = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val retrieveDirectory = unbuilder.readField[Option[java.io.File]]("retrieveDirectory")
      val outputPattern = unbuilder.readField[Option[String]]("outputPattern")
      val sync = unbuilder.readField[Boolean]("sync")
      val configurationsToRetrieve = unbuilder.readField[Option[scala.Vector[sbt.librarymanagement.ConfigRef]]]("configurationsToRetrieve")
      unbuilder.endObject()
      sbt.librarymanagement.RetrieveConfiguration(retrieveDirectory, outputPattern, sync, configurationsToRetrieve)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.RetrieveConfiguration, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("retrieveDirectory", obj.retrieveDirectory)
    builder.addField("outputPattern", obj.outputPattern)
    builder.addField("sync", obj.sync)
    builder.addField("configurationsToRetrieve", obj.configurationsToRetrieve)
    builder.endObject()
  }
}
}
