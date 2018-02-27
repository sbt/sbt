package coursier.cli.util

import java.util.zip.{ZipEntry, ZipInputStream}

object Zip {

  def zipEntries(zipStream: ZipInputStream): Iterator[(ZipEntry, Array[Byte])] =
    new Iterator[(ZipEntry, Array[Byte])] {
      var nextEntry = Option.empty[ZipEntry]
      def update() =
        nextEntry = Option(zipStream.getNextEntry)

      update()

      def hasNext = nextEntry.nonEmpty
      def next() = {
        val ent = nextEntry.get
        val data = coursier.internal.FileUtil.readFully(zipStream)

        update()

        (ent, data)
      }
    }

}
