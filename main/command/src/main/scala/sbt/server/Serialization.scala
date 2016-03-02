/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.typesafe.com>
 */
package sbt
package server

import org.json4s.JsonAST.{ JArray, JString }
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.ParserUtil.ParseException

object Serialization {

  def serialize(event: Event): Array[Byte] = {
    compact(render(toJson(event))).getBytes("UTF-8")
  }

  def toJson(event: Event): JObject = event match {
    case LogEvent() =>
      JObject(
        "type" -> JString("log_event"),
        "level" -> JString("INFO"),
        "message" -> JString("todo")
      )

    case StatusEvent() =>
      JObject(
        "type" -> JString("status_event"),
        "status" -> JString("ready"),
        "command_queue" -> JArray(List.empty)
      )

    case ExecutionEvent() =>
      JObject(
        "type" -> JString("execution_event"),
        "command" -> JString("project todo"),
        "success" -> JArray(List.empty)
      )
  }

  /**
   * @return A command or an invalid input description
   */
  def deserialize(bytes: Seq[Byte]): Either[String, Command] =
    try {
      val json = parse(new String(bytes.toArray, "UTF-8"))
      implicit val formats = DefaultFormats

      // TODO: is using extract safe?
      (json \ "type").extract[String] match {
        case "execution" => Right(Execution((json \ "command_line").extract[String]))
        case cmd         => Left(s"Unknown command type $cmd")
      }
    } catch {
      case e: ParseException   => Left(s"Parse error: ${e.getMessage}")
      case e: MappingException => Left(s"Missing type field")
    }
}
