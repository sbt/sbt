/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ArtifactTypeFilterFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val ArtifactTypeFilterFormat: JsonFormat[sbt.librarymanagement.ArtifactTypeFilter] = new JsonFormat[sbt.librarymanagement.ArtifactTypeFilter] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.ArtifactTypeFilter = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val types = unbuilder.readField[Set[String]]("types")
      val inverted = unbuilder.readField[Boolean]("inverted")
      unbuilder.endObject()
      sbt.librarymanagement.ArtifactTypeFilter(types, inverted)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.ArtifactTypeFilter, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("types", obj.types)
    builder.addField("inverted", obj.inverted)
    builder.endObject()
  }
}
}
