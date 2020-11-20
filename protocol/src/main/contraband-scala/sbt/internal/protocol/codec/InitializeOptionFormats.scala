/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait InitializeOptionFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val InitializeOptionFormat: JsonFormat[sbt.internal.protocol.InitializeOption] = new JsonFormat[sbt.internal.protocol.InitializeOption] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.protocol.InitializeOption = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val token = unbuilder.readField[Option[String]]("token")
      val skipAnalysis = unbuilder.readField[Option[Boolean]]("skipAnalysis")
      unbuilder.endObject()
      sbt.internal.protocol.InitializeOption(token, skipAnalysis)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.protocol.InitializeOption, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("token", obj.token)
    builder.addField("skipAnalysis", obj.skipAnalysis)
    builder.endObject()
  }
}
}
