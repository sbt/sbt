/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait InitializeOptionFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val InitializeOptionFormat: JsonFormat[sbt.internal.protocol.InitializeOption] = new JsonFormat[sbt.internal.protocol.InitializeOption] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.protocol.InitializeOption = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val token = unbuilder.readField[Option[String]]("token")
      unbuilder.endObject()
      sbt.internal.protocol.InitializeOption(token)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.protocol.InitializeOption, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("token", obj.token)
    builder.endObject()
  }
}
}
