/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait PatternsFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val PatternsFormat: JsonFormat[sbt.librarymanagement.Patterns] = new JsonFormat[sbt.librarymanagement.Patterns] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.Patterns = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val ivyPatterns = unbuilder.readField[Vector[String]]("ivyPatterns")
      val artifactPatterns = unbuilder.readField[Vector[String]]("artifactPatterns")
      val isMavenCompatible = unbuilder.readField[Boolean]("isMavenCompatible")
      val descriptorOptional = unbuilder.readField[Boolean]("descriptorOptional")
      val skipConsistencyCheck = unbuilder.readField[Boolean]("skipConsistencyCheck")
      unbuilder.endObject()
      sbt.librarymanagement.Patterns(ivyPatterns, artifactPatterns, isMavenCompatible, descriptorOptional, skipConsistencyCheck)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.Patterns, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("ivyPatterns", obj.ivyPatterns)
    builder.addField("artifactPatterns", obj.artifactPatterns)
    builder.addField("isMavenCompatible", obj.isMavenCompatible)
    builder.addField("descriptorOptional", obj.descriptorOptional)
    builder.addField("skipConsistencyCheck", obj.skipConsistencyCheck)
    builder.endObject()
  }
}
}
