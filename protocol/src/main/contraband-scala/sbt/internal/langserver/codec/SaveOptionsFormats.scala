/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait SaveOptionsFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val SaveOptionsFormat: JsonFormat[sbt.internal.langserver.SaveOptions] = new JsonFormat[sbt.internal.langserver.SaveOptions] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.SaveOptions = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val includeText = unbuilder.readField[Option[Boolean]]("includeText")
      unbuilder.endObject()
      sbt.internal.langserver.SaveOptions(includeText)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.SaveOptions, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("includeText", obj.includeText)
    builder.endObject()
  }
}
}
