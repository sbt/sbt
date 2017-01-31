package sbt
package internal
package util

import sbt.util.Level
import sjsonnew.JsonFormat

final class ObjectEvent[A](
  val level: Level.Value,
  val message: A,
  val channelName: Option[String],
  val execId: Option[String],
  val tag: String
) extends Serializable {
}
