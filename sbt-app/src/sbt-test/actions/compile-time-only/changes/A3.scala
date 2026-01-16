import sbt.*, Keys.*
import Def.Initialize
import complete.{DefaultParsers, Parser}
import sbt.librarymanagement.LibraryManagementCodec.given

object A:
  val x1: Initialize[Parser[Int]] = Def.setting { DefaultParsers.success(3) }
  val y1 = Def.task { x1.parsed }
