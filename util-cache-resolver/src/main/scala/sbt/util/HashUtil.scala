package sbt.util

import java.nio.ByteBuffer
import java.nio.file.{ Files, Path }
import java.security.MessageDigest
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

  def sha256HashStr(longs: Array[Long]): String =
    "sha256-" + toHexString(sha256Hash(longs))

  def toHexString(bytes: Array[Byte]): String =
    val sb = new StringBuilder
    for b <- bytes do sb.append(f"${b & 0xff}%02x")
    sb.toString

  def longsToBytes(longs: Array[Long]): Array[Byte] =
    val buffer = ByteBuffer.allocate(longs.length * java.lang.Long.BYTES)
    for l <- longs do buffer.putLong(l)
    buffer.array()
end HashUtil
