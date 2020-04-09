/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.testing.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TestResultFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val TestResultFormat: JsonFormat[sbt.protocol.testing.TestResult] = new JsonFormat[sbt.protocol.testing.TestResult] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.testing.TestResult = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.readString(__js) match {
        case "Passed" => sbt.protocol.testing.TestResult.Passed
        case "Failed" => sbt.protocol.testing.TestResult.Failed
        case "Error" => sbt.protocol.testing.TestResult.Error
      }
      case None =>
      deserializationError("Expected JsString but found None")
    }
  }
  override def write[J](obj: sbt.protocol.testing.TestResult, builder: Builder[J]): Unit = {
    val str = obj match {
      case sbt.protocol.testing.TestResult.Passed => "Passed"
      case sbt.protocol.testing.TestResult.Failed => "Failed"
      case sbt.protocol.testing.TestResult.Error => "Error"
    }
    builder.writeString(str)
  }
}
}
