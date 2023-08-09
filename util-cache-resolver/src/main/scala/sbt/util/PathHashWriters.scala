package sbt.util

import java.io.File
import java.nio.file.Path
import sjsonnew.{ Builder, HashWriter, JsonWriter }
import StringLongs.StringLong
import xsbti.VirtualFile

trait PathHashWriters:
  given stringLongLike[A](using conv: Conversion[A, StringLong]): HashWriter[A] with
    def write[J](obj: A, builder: Builder[J]): Unit =
      val ev = summon[HashWriter[StringLong]]
      ev.write(conv(obj), builder)
end PathHashWriters

object PathHashWriters extends PathHashWriters

// Use opaque type to define HashWriter instance
object StringLongs:
  opaque type StringLong = (String, Long)
  object StringLong:
    def apply(first: String, second: Long): StringLong = (first, second)

    given Conversion[HashedVirtualFileRef, StringLong] =
      (x: HashedVirtualFileRef) => StringLong(x.id, x.contentHash)
    given Conversion[File, StringLong] =
      (x: File) => StringLong(x.toString(), HashUtil.farmHash(x.toPath()))
    given Conversion[Path, StringLong] =
      (x: Path) => StringLong(x.toString(), HashUtil.farmHash(x))
    given Conversion[VirtualFile, StringLong] =
      (x: VirtualFile) => StringLong(x.id, x.contentHash)

    given HashWriter[StringLong] = new HashWriter[StringLong]:
      def write[J](obj: StringLong, builder: Builder[J]): Unit =
        builder.beginObject()
        builder.addFieldName("first")
        builder.writeString(obj._1)
        builder.addFieldName("second")
        builder.writeLong(obj._2)
        builder.endObject()
end StringLongs
