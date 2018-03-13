package coursier.internal

import java.io.{ByteArrayOutputStream, File, FileInputStream, FileOutputStream, InputStream}
import java.nio.file.Files

object FileUtil {

  def write(file: File, bytes: Array[Byte]): Unit = {
    var fos: FileOutputStream = null
    try {
      fos = new FileOutputStream(file)
      fos.write(bytes)
      fos.close()
    } finally {
      if (fos != null) fos.close()
    }
  }

  def readFully(is: InputStream): Array[Byte] = {
    val buffer = new ByteArrayOutputStream
    val data = Array.ofDim[Byte](16384)

    var nRead = 0
    while ({
      nRead = is.read(data, 0, data.length)
      nRead != -1
    })
      buffer.write(data, 0, nRead)

    buffer.flush()
    buffer.toByteArray
  }

  def readAllBytes(file: File): Array[Byte] = {
    var fis: FileInputStream = null
    try {
      fis = new FileInputStream(file)
      readFully(fis)
    } finally {
      if (fis != null)
        fis.close()
    }
  }

  def createTempDirectory(prefix: String): File =
    Files.createTempDirectory(prefix).toFile

}