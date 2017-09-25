/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait PortFileFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val PortFileFormat: JsonFormat[sbt.internal.protocol.PortFile] = new JsonFormat[sbt.internal.protocol.PortFile] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.protocol.PortFile = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val uri = unbuilder.readField[String]("uri")
      val tokenfilePath = unbuilder.readField[Option[String]]("tokenfilePath")
      val tokenfileUri = unbuilder.readField[Option[String]]("tokenfileUri")
      unbuilder.endObject()
      sbt.internal.protocol.PortFile(uri, tokenfilePath, tokenfileUri)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.protocol.PortFile, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("uri", obj.uri)
    builder.addField("tokenfilePath", obj.tokenfilePath)
    builder.addField("tokenfileUri", obj.tokenfileUri)
    builder.endObject()
  }
}
}
