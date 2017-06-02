
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}
import java.io.{ByteArrayOutputStream, FileInputStream, FileOutputStream, InputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import sbt.File

object ZipUtil {

  def addToZip(sourceZip: File, destZip: File, extra: Seq[(String, Array[Byte])]): Unit = {
    
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

    for ((dest, data) <- extra) {
      outputZip.putNextEntry(new ZipEntry(dest))
      outputZip.write(data)
      outputZip.closeEntry()
    }

    outputZip.close()

    is.close()
    os.close()

  }

  // seems the -noverify may not be needed anymore (to launch the proguarded JAR)
  // not sure why
  private val prelude =
    """#!/usr/bin/env bash
      |exec java -noverify -jar "$0" "$@"
      |""".stripMargin

  def addPrelude(source: File, dest: File): dest.type = {

    val rawContent = Files.readAllBytes(source.toPath)
    val content = prelude.getBytes(StandardCharsets.UTF_8) ++ rawContent

    Files.write(dest.toPath, content)

    dest
  }

}
