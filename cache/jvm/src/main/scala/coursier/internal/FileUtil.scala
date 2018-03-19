package coursier.internal

import java.io.{ByteArrayOutputStream, InputStream}

object FileUtil {

  // Won't be necessary anymore with Java 9
  // (https://docs.oracle.com/javase/9/docs/api/java/io/InputStream.html#readAllBytes--,
  // via https://stackoverflow.com/questions/1264709/convert-inputstream-to-byte-array-in-java/37681322#37681322)
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

}