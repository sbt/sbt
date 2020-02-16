/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait BuildTargetIdentifierFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val BuildTargetIdentifierFormat: JsonFormat[sbt.internal.bsp.BuildTargetIdentifier] = new JsonFormat[sbt.internal.bsp.BuildTargetIdentifier] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.BuildTargetIdentifier = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val uri = unbuilder.readField[java.net.URI]("uri")
      unbuilder.endObject()
      sbt.internal.bsp.BuildTargetIdentifier(uri)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.BuildTargetIdentifier, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("uri", obj.uri)
    builder.endObject()
  }
}
}
