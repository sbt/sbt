/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait URLRepositoryFormats { self: sbt.librarymanagement.PatternsFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val URLRepositoryFormat: JsonFormat[sbt.librarymanagement.URLRepository] = new JsonFormat[sbt.librarymanagement.URLRepository] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.URLRepository = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val name = unbuilder.readField[String]("name")
      val patterns = unbuilder.readField[sbt.librarymanagement.Patterns]("patterns")
      val allowInsecureProtocol = unbuilder.readField[Boolean]("allowInsecureProtocol")
      unbuilder.endObject()
      sbt.librarymanagement.URLRepository(name, patterns, allowInsecureProtocol)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.URLRepository, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("name", obj.name)
    builder.addField("patterns", obj.patterns)
    builder.addField("allowInsecureProtocol", obj.allowInsecureProtocol)
    builder.endObject()
  }
}
}
