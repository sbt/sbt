/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait UpdateLoggingFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val UpdateLoggingFormat: JsonFormat[sbt.librarymanagement.UpdateLogging] = new JsonFormat[sbt.librarymanagement.UpdateLogging] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.UpdateLogging = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.readString(__js) match {
        case "Full" => sbt.librarymanagement.UpdateLogging.Full
        case "DownloadOnly" => sbt.librarymanagement.UpdateLogging.DownloadOnly
        case "Quiet" => sbt.librarymanagement.UpdateLogging.Quiet
        case "Default" => sbt.librarymanagement.UpdateLogging.Default
      }
      case None =>
      deserializationError("Expected JsString but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.UpdateLogging, builder: Builder[J]): Unit = {
    val str = obj match {
      case sbt.librarymanagement.UpdateLogging.Full => "Full"
      case sbt.librarymanagement.UpdateLogging.DownloadOnly => "DownloadOnly"
      case sbt.librarymanagement.UpdateLogging.Quiet => "Quiet"
      case sbt.librarymanagement.UpdateLogging.Default => "Default"
    }
    builder.writeString(str)
  }
}
}
