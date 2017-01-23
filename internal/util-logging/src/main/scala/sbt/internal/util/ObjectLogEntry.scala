package sbt
package internal
package util

import sbt.util.Level
import sjsonnew.JsonFormat

final class ObjectLogEntry[A](
  val level: Level.Value,
  val message: A,
  val channelName: Option[String],
  val execId: Option[String],
  val ev: JsonFormat[A],
  val clazz: Class[A]
) extends Serializable {

}
