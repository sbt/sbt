package sbt.internal.librarymanagement.formats

import sjsonnew._
import xsbti._
import sbt.util.Logger.Null

/**
 * A fake JsonFormat for xsbti.Logger.
 * This is mostly for making IvyConfiguration serializable to JSON.
 */
trait LoggerFormat { self: BasicJsonProtocol =>
  implicit lazy val xsbtiLoggerIsoString: IsoString[Logger] =
    IsoString.iso(_ => "<logger>", _ => Null)

  implicit lazy val LoggerFormat: JsonFormat[Logger] = isoStringFormat(implicitly)
}
