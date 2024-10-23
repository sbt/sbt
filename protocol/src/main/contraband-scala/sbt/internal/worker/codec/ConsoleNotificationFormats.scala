/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ConsoleNotificationFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val ConsoleNotificationFormat: JsonFormat[sbt.internal.worker.ConsoleNotification] = new JsonFormat[sbt.internal.worker.ConsoleNotification] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.worker.ConsoleNotification = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val ref = unbuilder.readField[String]("ref")
      val stdout = unbuilder.readField[Option[String]]("stdout")
      val stderr = unbuilder.readField[Option[String]]("stderr")
      unbuilder.endObject()
      sbt.internal.worker.ConsoleNotification(ref, stdout, stderr)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.worker.ConsoleNotification, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("ref", obj.ref)
    builder.addField("stdout", obj.stdout)
    builder.addField("stderr", obj.stderr)
    builder.endObject()
  }
}
}
