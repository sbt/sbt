package coursier.internal

import java.io._
import java.util.UUID

/** Java 6-compatible helpers mimicking NIO */
object FileUtil {

  private object Java7 {

    import java.nio.file.{ Files, StandardCopyOption }

    def atomicMove(from: File, to: File): Unit =
      Files.move(from.toPath, to.toPath, StandardCopyOption.ATOMIC_MOVE)

    def createTempDirectory(prefix: String): File =
      Files.createTempDirectory(prefix).toFile
  }

  private object Java6 {
    def move(from: File, to: File): Unit =
      if (!from.renameTo(to))
        throw new IOException(s"Cannot move $from to $to")

    def createTempDirectory(prefix: String): File = {
      val tmpBaseDir = new File(sys.props("java.io.tmpdir"))
      val tmpDir = new File(tmpBaseDir, s"$prefix-${UUID.randomUUID()}")
      tmpDir.mkdirs()
      tmpDir
    }
  }

  private def versionGteq(version: String, to: (Int, Int)): Boolean =
    version.split('.').take(2).map(s => scala.util.Try(s.toInt).toOption) match {
      case Array(Some(major), Some(minor)) =>
        Ordering[(Int, Int)].gteq((major, minor), (1, 7))
      case _ => false
    }

  // Fine if set several times (if java7Available() is initially called concurrently)
  @volatile private var java7AvailableOpt = Option.empty[Boolean]
  private def java7Available(): Boolean =
    java7AvailableOpt.getOrElse {
      val available = sys.props.get("java.version").exists { version =>
        versionGteq(version, (1, 7))
      }
      java7AvailableOpt = Some(available)
      available
    }

  /** Not guaranteed to be atomic on Java 6 */
  def atomicMove(from: File, to: File): Unit =
    if (java7Available())
      Java7.atomicMove(from, to)
    else
      Java6.move(from, to)

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
    if (java7Available())
      Java7.createTempDirectory(prefix)
    else
      Java6.createTempDirectory(prefix)

}