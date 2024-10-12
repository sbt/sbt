/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait GeneralParamsFormats { self: sbt.internal.worker.codec.HashedPathFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val GeneralParamsFormat: JsonFormat[sbt.internal.worker.GeneralParams] = new JsonFormat[sbt.internal.worker.GeneralParams] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.worker.GeneralParams = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val main_class = unbuilder.readField[String]("main_class")
      val args = unbuilder.readField[Vector[String]]("args")
      val classpath = unbuilder.readField[Vector[sbt.internal.worker.HashedPath]]("classpath")
      unbuilder.endObject()
      sbt.internal.worker.GeneralParams(main_class, args, classpath)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.worker.GeneralParams, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("main_class", obj.main_class)
    builder.addField("args", obj.args)
    builder.addField("classpath", obj.classpath)
    builder.endObject()
  }
}
}
