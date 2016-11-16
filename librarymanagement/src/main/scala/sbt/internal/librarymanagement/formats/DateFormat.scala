package sbt.internal.librarymanagement.formats

import sjsonnew._
import java.util._
import java.text._

trait DateFormat { self: BasicJsonProtocol =>
  private val format = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy")

  implicit lazy val DateFormat: JsonFormat[Date] = project(_.toString, format.parse _)
}
