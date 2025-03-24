/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait LicenseFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val LicenseFormat: JsonFormat[sbt.librarymanagement.License] = new JsonFormat[sbt.librarymanagement.License] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.License = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val spdxId = unbuilder.readField[String]("spdxId")
      val uri = unbuilder.readField[java.net.URI]("uri")
      val distribution = unbuilder.readField[Option[String]]("distribution")
      val comments = unbuilder.readField[Option[String]]("comments")
      unbuilder.endObject()
      sbt.librarymanagement.License(spdxId, uri, distribution, comments)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.License, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("spdxId", obj.spdxId)
    builder.addField("uri", obj.uri)
    builder.addField("distribution", obj.distribution)
    builder.addField("comments", obj.comments)
    builder.endObject()
  }
}
}
