/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait PomConfigurationFormats { self: sbt.librarymanagement.ScalaModuleInfoFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val PomConfigurationFormat: JsonFormat[sbt.librarymanagement.PomConfiguration] = new JsonFormat[sbt.librarymanagement.PomConfiguration] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.PomConfiguration = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val validate = unbuilder.readField[Boolean]("validate")
      val scalaModuleInfo = unbuilder.readField[Option[sbt.librarymanagement.ScalaModuleInfo]]("scalaModuleInfo")
      val file = unbuilder.readField[java.io.File]("file")
      val autoScalaTools = unbuilder.readField[Boolean]("autoScalaTools")
      unbuilder.endObject()
      sbt.librarymanagement.PomConfiguration(validate, scalaModuleInfo, file, autoScalaTools)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.PomConfiguration, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("validate", obj.validate)
    builder.addField("scalaModuleInfo", obj.scalaModuleInfo)
    builder.addField("file", obj.file)
    builder.addField("autoScalaTools", obj.autoScalaTools)
    builder.endObject()
  }
}
}
