/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait LogOptionFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val LogOptionFormat: JsonFormat[sbt.internal.util.LogOption] = new JsonFormat[sbt.internal.util.LogOption] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.util.LogOption = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.readString(__js) match {
        case "Always" => sbt.internal.util.LogOption.Always
        case "Never" => sbt.internal.util.LogOption.Never
        case "Auto" => sbt.internal.util.LogOption.Auto
      }
      case None =>
      deserializationError("Expected JsString but found None")
    }
  }
  override def write[J](obj: sbt.internal.util.LogOption, builder: Builder[J]): Unit = {
    val str = obj match {
      case sbt.internal.util.LogOption.Always => "Always"
      case sbt.internal.util.LogOption.Never => "Never"
      case sbt.internal.util.LogOption.Auto => "Auto"
    }
    builder.writeString(str)
  }
}
}
