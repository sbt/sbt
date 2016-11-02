package sbt.internal.librarymanagement.formats

import sjsonnew._
import xsbti._

trait GlobalLockFormat { self: BasicJsonProtocol =>
  implicit lazy val GlobalLockFormat: JsonFormat[GlobalLock] =
    project(MyCrazyReferences.referenced _, (ref: String) => MyCrazyReferences(ref, classOf[GlobalLock]))
}
