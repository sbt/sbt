package sbt.internal.librarymanagement.formats

import sjsonnew._
import scala.xml._

trait NodeSeqFormat { self: BasicJsonProtocol =>
  implicit lazy val NodeSeqFormat: JsonFormat[NodeSeq] =
    project(MyCrazyReferences.referenced _, (ref: String) => MyCrazyReferences(ref, classOf[NodeSeq]))
}
