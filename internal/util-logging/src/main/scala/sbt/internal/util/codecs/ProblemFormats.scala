/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

package sbt.internal.util.codec

import xsbti.{ Problem, Severity, Position }
import sbt.util.InterfaceUtil.problem
import _root_.sjsonnew.{ deserializationError, Builder, JsonFormat, Unbuilder }

trait ProblemFormats { self: SeverityFormats with PositionFormats with sjsonnew.BasicJsonProtocol =>
  implicit lazy val ProblemFormat: JsonFormat[Problem] = new JsonFormat[Problem] {
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): Problem = {
      jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          val category = unbuilder.readField[String]("category")
          val severity = unbuilder.readField[Severity]("severity")
          val message = unbuilder.readField[String]("message")
          val position = unbuilder.readField[Position]("position")
          unbuilder.endObject()
          problem(category, position, message, severity)
        case None =>
          deserializationError("Expected JsObject but found None")
      }
    }
    override def write[J](obj: Problem, builder: Builder[J]): Unit = {
      builder.beginObject()
      builder.addField("category", obj.category)
      builder.addField("severity", obj.severity)
      builder.addField("message", obj.message)
      builder.addField("position", obj.position)
      builder.endObject()
    }
  }
}
