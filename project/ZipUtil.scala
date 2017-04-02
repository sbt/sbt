
import java.util.zip.{ ZipEntry, ZipOutputStream, ZipInputStream }
import java.io.{ ByteArrayOutputStream, FileInputStream, FileOutputStream, File, InputStream, IOException }

object ZipUtil {

  def addToZip(sourceZip: File, destZip: File, extra: Seq[(String, File)]): Unit = {
    
    val is = new FileInputStream(sourceZip)
    val os = new FileOutputStream(destZip)
    val bootstrapZip = new ZipInputStream(is)
    val outputZip = new ZipOutputStream(os)

    def readFullySync(is: InputStream) = {
      val buffer = new ByteArrayOutputStream
      val data = Array.ofDim[Byte](16384)

      var nRead = is.read(data, 0, data.length)
      while (nRead != -1) {
        buffer.write(data, 0, nRead)
        nRead = is.read(data, 0, data.length)
      }

      buffer.flush()
      buffer.toByteArray
    }

    def zipEntries(zipStream: ZipInputStream): Iterator[(ZipEntry, Array[Byte])] =
      new Iterator[(ZipEntry, Array[Byte])] {
        private var nextEntry = Option.empty[ZipEntry]
        private def update() =
          nextEntry = Option(zipStream.getNextEntry)

        update()

        def hasNext = nextEntry.nonEmpty
        def next() = {
          val ent = nextEntry.get
          val data = readFullySync(zipStream)

          update()

          (ent, data)
        }
      }

    for ((ent, data) <- zipEntries(bootstrapZip)) {
      outputZip.putNextEntry(ent)
      outputZip.write(data)
      outputZip.closeEntry()
    }

    for ((dest, file) <- extra) {
      outputZip.putNextEntry(new ZipEntry(dest))
      outputZip.write(readFullySync(new FileInputStream(file)))
      outputZip.closeEntry()
    }

    outputZip.close()

    is.close()
    os.close()

  }

}
