package sbt.util

import java.io.{ BufferedInputStream, InputStream }
import java.nio.ByteBuffer
import java.nio.file.{ Files, Path }
import java.security.{ DigestInputStream, MessageDigest }
import net.openhft.hashing.LongHashFunction
import scala.util.Try

object HashUtil:
  private[sbt] def farmHash(bytes: Array[Byte]): Long =
    LongHashFunction.farmNa().hashBytes(bytes)

  private[sbt] def farmHash(path: Path): Long =
    import sbt.io.Hash
    // allocating many byte arrays for large files may lead to OOME
    // but it is more efficient for small files
    val largeFileLimit = 10 * 1024 * 1024

    if Files.size(path) < largeFileLimit then farmHash(Files.readAllBytes(path))
    else farmHash(Hash(path.toFile))

  private[sbt] def farmHashStr(path: Path): String =
    "farm64-" + farmHash(path).toHexString

  private[sbt] def toFarmHashString(digest: Long): String =
    s"farm64-${digest.toHexString}"

  def sha256Hash(bytes: Array[Byte]): Array[Byte] =
    val digest = MessageDigest.getInstance("SHA-256")
    digest.digest(bytes)

  def sha256Hash(longs: Array[Long]): Array[Byte] =
    sha256Hash(longsToBytes(longs))

  def sha256Hash(input: InputStream): Array[Byte] =
    val BufferSize = 8192
    val bis = BufferedInputStream(input)
    val digest = MessageDigest.getInstance("SHA-256")
    try
      val dis = DigestInputStream(bis, digest)
      val buffer = new Array[Byte](BufferSize)
      while dis.read(buffer) >= 0 do ()
      dis.close()
      digest.digest
    finally bis.close()

  def sha256HashStr(longs: Array[Long]): String =
    "sha256-" + toHexString(sha256Hash(longs))

  def sha256HashStr(input: InputStream): String =
    "sha256-" + toHexString(sha256Hash(input))

  def toHexString(bytes: Array[Byte]): String =
    val sb = new StringBuilder
    for b <- bytes do sb.append(f"${b & 0xff}%02x")
    sb.toString

  def longsToBytes(longs: Array[Long]): Array[Byte] =
    val buffer = ByteBuffer.allocate(longs.length * java.lang.Long.BYTES)
    for l <- longs do buffer.putLong(l)
    buffer.array()
end HashUtil
