package sbt.internal.librarymanagement.formats

import sjsonnew._

trait Function1Format { self: BasicJsonProtocol =>
  implicit def Function1Format[T, U]: JsonFormat[T => U] =
    project(MyCrazyReferences.referenced _, (ref: String) => MyCrazyReferences(ref, classOf[T => U]))
}
