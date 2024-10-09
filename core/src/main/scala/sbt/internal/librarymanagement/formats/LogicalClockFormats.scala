package sbt.internal.librarymanagement.formats

import sjsonnew._

import sbt.librarymanagement.LogicalClock

trait LogicalClockFormats { self: BasicJsonProtocol =>
  implicit lazy val LogicalClockFormat: JsonFormat[LogicalClock] =
    projectFormat[LogicalClock, String](
      cl => cl.toString,
      str => LogicalClock(str)
    )
}
