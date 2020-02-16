/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait BuildTargetIdentiferFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val BuildTargetIdentiferFormat: JsonFormat[sbt.internal.bsp.BuildTargetIdentifer] = new JsonFormat[sbt.internal.bsp.BuildTargetIdentifer] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.BuildTargetIdentifer = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val uri = unbuilder.readField[java.net.URI]("uri")
      unbuilder.endObject()
      sbt.internal.bsp.BuildTargetIdentifer(uri)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.BuildTargetIdentifer, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("uri", obj.uri)
    builder.endObject()
  }
}
}
