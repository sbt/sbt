/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait FileRepositoryFormats { self: sbt.librarymanagement.PatternsFormats with sjsonnew.BasicJsonProtocol with sbt.librarymanagement.FileConfigurationFormats =>
implicit lazy val FileRepositoryFormat: JsonFormat[sbt.librarymanagement.FileRepository] = new JsonFormat[sbt.librarymanagement.FileRepository] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.FileRepository = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val name = unbuilder.readField[String]("name")
      val patterns = unbuilder.readField[sbt.librarymanagement.Patterns]("patterns")
      val configuration = unbuilder.readField[sbt.librarymanagement.FileConfiguration]("configuration")
      unbuilder.endObject()
      sbt.librarymanagement.FileRepository(name, patterns, configuration)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.FileRepository, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("name", obj.name)
    builder.addField("patterns", obj.patterns)
    builder.addField("configuration", obj.configuration)
    builder.endObject()
  }
}
}
