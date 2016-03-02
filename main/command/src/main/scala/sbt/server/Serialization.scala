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
    case LogEvent(level, message) =>
      JObject(
        "type" -> JString("log_event"),
        "level" -> JString(level),
        "message" -> JString(message)
      )

    case StatusEvent(Ready) =>
      JObject(
        "type" -> JString("status_event"),
        "status" -> JString("ready"),
        "command_queue" -> JArray(List.empty)
      )

    case StatusEvent(Processing(command, commandQueue)) =>
      JObject(
        "type" -> JString("status_event"),
        "status" -> JString("processing"),
        "command_queue" -> JArray(commandQueue.map(JString).toList)
      )

    case ExecutionEvent(command, status) =>
      JObject(
        "type" -> JString("execution_event"),
        "command" -> JString(command),
        "success" -> JBool(status)
      )
  }

  /**
   * @return A command or an invalid input description
   */
  def deserialize(bytes: Seq[Byte]): Either[String, Command] =
    try {
      val json = parse(new String(bytes.toArray, "UTF-8"))
      implicit val formats = DefaultFormats

      (json \ "type").toOption match {
        case Some(JString("execution")) =>
          (json \ "command_line").toOption match {
            case Some(JString(cmd)) => Right(Execution(cmd))
            case _                  => Left("Missing or invalid command_line field")
          }
        case Some(cmd) => Left(s"Unknown command type $cmd")
        case None      => Left("Invalid command, missing type field")
      }
    } catch {
      case e: ParseException => Left(s"Parse error: ${e.getMessage}")
    }
}
