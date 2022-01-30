/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait IvyFileConfigurationFormats { self: sbt.librarymanagement.ScalaModuleInfoFormats with sbt.librarymanagement.ConfigurationFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val IvyFileConfigurationFormat: JsonFormat[sbt.librarymanagement.IvyFileConfiguration] = new JsonFormat[sbt.librarymanagement.IvyFileConfiguration] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.IvyFileConfiguration = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val validate = unbuilder.readField[Boolean]("validate")
      val scalaModuleInfo = unbuilder.readField[Option[sbt.librarymanagement.ScalaModuleInfo]]("scalaModuleInfo")
      val file = unbuilder.readField[java.io.File]("file")
      val autoScalaTools = unbuilder.readField[Boolean]("autoScalaTools")
      unbuilder.endObject()
      sbt.librarymanagement.IvyFileConfiguration(validate, scalaModuleInfo, file, autoScalaTools)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.IvyFileConfiguration, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("validate", obj.validate)
    builder.addField("scalaModuleInfo", obj.scalaModuleInfo)
    builder.addField("file", obj.file)
    builder.addField("autoScalaTools", obj.autoScalaTools)
    builder.endObject()
  }
}
}
