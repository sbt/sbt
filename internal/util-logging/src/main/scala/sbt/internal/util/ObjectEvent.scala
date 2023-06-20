/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package util

import sbt.util.Level
import sjsonnew.JsonFormat
import sjsonnew.support.scalajson.unsafe.Converter
import sjsonnew.shaded.scalajson.ast.unsafe.JValue

final class ObjectEvent[A](
    val level: Level.Value,
    val message: A,
    val channelName: Option[String],
    val execId: Option[String],
    val contentType: String,
    val json: JValue
) extends Serializable {
  override def toString: String =
    s"ObjectEvent($level, $message, $channelName, $execId, $contentType, $json)"
}

object ObjectEvent {
  def apply[A: JsonFormat](
      level: Level.Value,
      message: A,
      channelName: Option[String],
      execId: Option[String],
      contentType: String
  ): ObjectEvent[A] =
    new ObjectEvent(
      level,
      message,
      channelName,
      execId,
      contentType,
      Converter.toJsonUnsafe(message)
    )
}
