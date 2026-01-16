package sbt.internal.librarymanagement.formats

import sjsonnew.*

import sbt.librarymanagement.LogicalClock

trait LogicalClockFormats { self: BasicJsonProtocol =>
  given LogicalClockFormat: JsonFormat[LogicalClock] =
    projectFormat[LogicalClock, String](
      cl => cl.toString,
      str => LogicalClock(str)
    )
}
