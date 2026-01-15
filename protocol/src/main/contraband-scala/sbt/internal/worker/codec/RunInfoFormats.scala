/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait RunInfoFormats { self: sbt.internal.worker.codec.JvmRunInfoFormats & sbt.internal.worker.codec.FilePathFormats & sjsonnew.BasicJsonProtocol & sbt.internal.worker.codec.NativeRunInfoFormats =>
given RunInfoFormat: JsonFormat[sbt.internal.worker.RunInfo] = new JsonFormat[sbt.internal.worker.RunInfo] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.worker.RunInfo = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val jvm = unbuilder.readField[Boolean]("jvm")
      val jvmRunInfo = unbuilder.readField[Option[sbt.internal.worker.JvmRunInfo]]("jvmRunInfo")
      val nativeRunInfo = unbuilder.readField[Option[sbt.internal.worker.NativeRunInfo]]("nativeRunInfo")
      val windowTitle = unbuilder.readField[Option[String]]("windowTitle")
      unbuilder.endObject()
      sbt.internal.worker.RunInfo(jvm, jvmRunInfo, nativeRunInfo, windowTitle)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.worker.RunInfo, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("jvm", obj.jvm)
    builder.addField("jvmRunInfo", obj.jvmRunInfo)
    builder.addField("nativeRunInfo", obj.nativeRunInfo)
    builder.addField("windowTitle", obj.windowTitle)
    builder.endObject()
  }
}
}
