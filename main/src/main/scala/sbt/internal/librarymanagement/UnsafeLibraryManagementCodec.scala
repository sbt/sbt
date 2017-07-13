package sbt.internal.librarymanagement

import sbt.librarymanagement.{ LibraryManagementCodec, ModuleID }
import sjsonnew.JsonFormat

object UnsafeLibraryManagementCodec {
  val moduleIdJsonKeyFormat: sjsonnew.JsonKeyFormat[ModuleID] = {
    new sjsonnew.JsonKeyFormat[ModuleID] {
      import sjsonnew.support.scalajson.unsafe._
      val moduleIdFormat: JsonFormat[ModuleID] = LibraryManagementCodec.ModuleIDFormat
      def write(key: ModuleID): String =
        CompactPrinter(Converter.toJsonUnsafe(key)(moduleIdFormat))
      def read(key: String): ModuleID =
        Converter.fromJsonUnsafe[ModuleID](Parser.parseUnsafe(key))(moduleIdFormat)
    }
  }
}
