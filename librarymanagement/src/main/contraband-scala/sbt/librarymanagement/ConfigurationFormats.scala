/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ConfigurationFormats { self: sbt.librarymanagement.ConfigurationFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val ConfigurationFormat: JsonFormat[sbt.librarymanagement.Configuration] = new JsonFormat[sbt.librarymanagement.Configuration] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.Configuration = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val name = unbuilder.readField[String]("name")
      val description = unbuilder.readField[String]("description")
      val isPublic = unbuilder.readField[Boolean]("isPublic")
      val extendsConfigs = unbuilder.readField[Vector[sbt.librarymanagement.Configuration]]("extendsConfigs")
      val transitive = unbuilder.readField[Boolean]("transitive")
      unbuilder.endObject()
      sbt.librarymanagement.Configuration(name, description, isPublic, extendsConfigs, transitive)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.Configuration, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("name", obj.name)
    builder.addField("description", obj.description)
    builder.addField("isPublic", obj.isPublic)
    builder.addField("extendsConfigs", obj.extendsConfigs)
    builder.addField("transitive", obj.transitive)
    builder.endObject()
  }
}
}
