/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.typesafe.com>
 */
package sbt.server

import org.json4s.JsonAST.{ JArray, JString }
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

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
  def deserialize(bytes: Seq[Byte]): Either[String, Command] = {
    val json = parse(new String(bytes.toArray, "UTF-8"))

    implicit val formats = DefaultFormats

    (json \ "type").extract[String] match {
      case "command" => Right(Execution((json \ "command_line").extract[String]))
      case cmd       => Left(s"Unknown command type $cmd")
    }
  }

}
