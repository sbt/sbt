/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TestResultFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val TestResultFormat: JsonFormat[sbt.internal.bsp.TestResult] = new JsonFormat[sbt.internal.bsp.TestResult] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.TestResult = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val originId = unbuilder.readField[Option[String]]("originId")
      val statusCode = unbuilder.readField[Int]("statusCode")
      unbuilder.endObject()
      sbt.internal.bsp.TestResult(originId, statusCode)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.TestResult, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("originId", obj.originId)
    builder.addField("statusCode", obj.statusCode)
    builder.endObject()
  }
}
}
