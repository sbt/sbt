package sbt.util

import java.io.File
import java.nio.file.Path
import sjsonnew.{ Builder, HashWriter, JsonWriter }
import StringStrings.StringString
import xsbti.{ HashedVirtualFileRef, VirtualFile }

trait PathHashWriters:
  given stringStringLike[A](using conv: Conversion[A, StringString]): HashWriter[A] with
    def write[J](obj: A, builder: Builder[J]): Unit =
      val ev = summon[HashWriter[StringString]]
      ev.write(conv(obj), builder)
end PathHashWriters

object PathHashWriters extends PathHashWriters

// Use opaque type to define HashWriter instance
object StringStrings:
  opaque type StringString = (String, String)
  object StringString:
    def apply(first: String, second: String): StringString = (first, second)

    given Conversion[HashedVirtualFileRef, StringString] =
      (x: HashedVirtualFileRef) => StringString(x.id, x.contentHashStr)
    given Conversion[File, StringString] =
      (x: File) => StringString(x.toString(), HashUtil.farmHashStr(x.toPath()))
    given Conversion[Path, StringString] =
      (x: Path) => StringString(x.toString(), HashUtil.farmHashStr(x))
    given Conversion[VirtualFile, StringString] =
      (x: VirtualFile) => StringString(x.id, s"farm64-${x.contentHash.toHexString}")

    given HashWriter[StringString] = new HashWriter[StringString]:
      def write[J](obj: StringString, builder: Builder[J]): Unit =
        builder.beginObject()
        builder.addFieldName("first")
        builder.writeString(obj._1)
        builder.addFieldName("second")
        builder.writeString(obj._2)
        builder.endObject()
end StringStrings
