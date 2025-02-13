/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait GeneralParamsFormats { self: sbt.internal.worker.codec.RunInfoFormats & sbt.internal.worker.codec.FilePathFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val GeneralParamsFormat: JsonFormat[sbt.internal.worker.GeneralParams] = new JsonFormat[sbt.internal.worker.GeneralParams] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.worker.GeneralParams = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val runInfo = unbuilder.readField[Option[sbt.internal.worker.RunInfo]]("runInfo")
      unbuilder.endObject()
      sbt.internal.worker.GeneralParams(runInfo)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.worker.GeneralParams, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("runInfo", obj.runInfo)
    builder.endObject()
  }
}
}
