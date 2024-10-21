/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TokenFileFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val TokenFileFormat: JsonFormat[sbt.internal.protocol.TokenFile] = new JsonFormat[sbt.internal.protocol.TokenFile] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.protocol.TokenFile = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val uri = unbuilder.readField[String]("uri")
      val token = unbuilder.readField[String]("token")
      unbuilder.endObject()
      sbt.internal.protocol.TokenFile(uri, token)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.protocol.TokenFile, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("uri", obj.uri)
    builder.addField("token", obj.token)
    builder.endObject()
  }
}
}
