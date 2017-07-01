/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait IvyFileConfigurationFormats { self: sbt.librarymanagement.IvyScalaFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val IvyFileConfigurationFormat: JsonFormat[sbt.librarymanagement.IvyFileConfiguration] = new JsonFormat[sbt.librarymanagement.IvyFileConfiguration] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.IvyFileConfiguration = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val validate = unbuilder.readField[Boolean]("validate")
      val ivyScala = unbuilder.readField[Option[sbt.librarymanagement.IvyScala]]("ivyScala")
      val file = unbuilder.readField[java.io.File]("file")
      val autoScalaTools = unbuilder.readField[Boolean]("autoScalaTools")
      unbuilder.endObject()
      sbt.librarymanagement.IvyFileConfiguration(validate, ivyScala, file, autoScalaTools)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.IvyFileConfiguration, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("validate", obj.validate)
    builder.addField("ivyScala", obj.ivyScala)
    builder.addField("file", obj.file)
    builder.addField("autoScalaTools", obj.autoScalaTools)
    builder.endObject()
  }
}
}
