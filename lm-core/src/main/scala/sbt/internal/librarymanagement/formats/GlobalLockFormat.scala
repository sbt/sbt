package sbt.internal.librarymanagement.formats

import sjsonnew._
import xsbti._
import java.io.File
import java.util.concurrent.Callable

/**
 * A fake JsonFormat for xsbti.GlobalLock.
 * This is mostly for making IvyConfiguration serializable to JSON.
 */
trait GlobalLockFormat { self: BasicJsonProtocol =>
  import GlobalLockFormats._

  given globalLockIsoString: IsoString[GlobalLock] =
    IsoString.iso(_ => "<lock>", _ => NoGlobalLock)

  given GlobalLockFormat: JsonFormat[GlobalLock] = isoStringFormat(using
    globalLockIsoString
  )
}

private[sbt] object GlobalLockFormats {
  object NoGlobalLock extends GlobalLock {
    def apply[T](lockFile: File, run: Callable[T]) = run.call()
  }
}
