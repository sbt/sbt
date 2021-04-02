/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait MetalsMetadataFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val MetalsMetadataFormat: JsonFormat[sbt.internal.bsp.MetalsMetadata] = new JsonFormat[sbt.internal.bsp.MetalsMetadata] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.MetalsMetadata = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val semanticdbVersion = unbuilder.readField[String]("semanticdbVersion")
      val supportedScalaVersions = unbuilder.readField[Vector[String]]("supportedScalaVersions")
      unbuilder.endObject()
      sbt.internal.bsp.MetalsMetadata(semanticdbVersion, supportedScalaVersions)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.MetalsMetadata, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("semanticdbVersion", obj.semanticdbVersion)
    builder.addField("supportedScalaVersions", obj.supportedScalaVersions)
    builder.endObject()
  }
}
}
