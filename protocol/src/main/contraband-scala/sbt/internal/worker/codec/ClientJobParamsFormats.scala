/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ClientJobParamsFormats { self: sbt.internal.worker.codec.RunInfoFormats & sbt.internal.worker.codec.JvmRunInfoFormats & sbt.internal.worker.codec.FilePathFormats & sjsonnew.BasicJsonProtocol & sbt.internal.worker.codec.NativeRunInfoFormats =>
implicit lazy val ClientJobParamsFormat: JsonFormat[sbt.internal.worker.ClientJobParams] = new JsonFormat[sbt.internal.worker.ClientJobParams] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.worker.ClientJobParams = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val runInfo = unbuilder.readField[Option[sbt.internal.worker.RunInfo]]("runInfo")
      unbuilder.endObject()
      sbt.internal.worker.ClientJobParams(runInfo)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.worker.ClientJobParams, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("runInfo", obj.runInfo)
    builder.endObject()
  }
}
}
