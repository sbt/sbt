/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait LicenseInfoFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val LicenseInfoFormat: JsonFormat[sbt.librarymanagement.LicenseInfo] = new JsonFormat[sbt.librarymanagement.LicenseInfo] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.LicenseInfo = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val name = unbuilder.readField[String]("name")
      val url = unbuilder.readField[java.net.URI]("url")
      val distribution = unbuilder.readField[Option[String]]("distribution")
      val comments = unbuilder.readField[Option[String]]("comments")
      unbuilder.endObject()
      sbt.librarymanagement.LicenseInfo(name, url, distribution, comments)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.LicenseInfo, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("name", obj.name)
    builder.addField("url", obj.url)
    builder.addField("distribution", obj.distribution)
    builder.addField("comments", obj.comments)
    builder.endObject()
  }
}
}
