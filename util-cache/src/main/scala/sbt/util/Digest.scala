package sbt.util

import sjsonnew.IsoString
import sbt.io.Hash
import java.io.{ BufferedInputStream, InputStream }
import java.nio.ByteBuffer
import java.security.{ DigestInputStream, MessageDigest }

opaque type Digest = String

object Digest:
  private val sha256_upper = "SHA-256"

  extension (d: Digest) def toBytes: Array[Byte] = parse(d)

  def apply(s: String): Digest =
    validateString(s)
    s

  def apply(algo: String, bytes: Array[Byte]): Digest =
    algo + "-" + toHexString(bytes)

  // used to wrap a Long value as a fake Digest, which will
  // later be hashed using sha256 anyway.
  def dummy(value: Long): Digest =
    apply("murmur3", longsToBytes(Array(0L, value)))

  lazy val zero: Digest = dummy(0L)

  def sha256Hash(bytes: Array[Byte]): Digest =
    apply("sha256", hashBytes(sha256_upper, bytes))

  def sha256Hash(longs: Array[Long]): Digest =
    apply("sha256", hashBytes(sha256_upper, longs))

  def sha256Hash(input: InputStream): Digest =
    apply("sha256", hashBytes(sha256_upper, input))

  def sha256Hash(digests: Digest*): Digest =
    sha256Hash(digests.toSeq.map(_.toBytes).flatten.toArray[Byte])

  private def hashBytes(algo: String, bytes: Array[Byte]): Array[Byte] =
    val digest = MessageDigest.getInstance(algo)
    digest.digest(bytes)

  private def hashBytes(algo: String, longs: Array[Long]): Array[Byte] =
    hashBytes(algo, longsToBytes(longs))

  private def hashBytes(algo: String, input: InputStream): Array[Byte] =
    val BufferSize = 8192
    val bis = BufferedInputStream(input)
    val digest = MessageDigest.getInstance(algo)
    try
      val dis = DigestInputStream(bis, digest)
      val buffer = new Array[Byte](BufferSize)
      while dis.read(buffer) >= 0 do ()
      dis.close()
      digest.digest
    finally bis.close()

  private def validateString(s: String): Unit =
    parse(s)
    ()

  private def parse(s: String): Array[Byte] =
    val tokens = s.split("-").toList
    tokens match
      case "murmur3" :: value :: Nil => parseHex(value, 128)
      case "md5" :: value :: Nil     => parseHex(value, 128)
      case "sha1" :: value :: Nil    => parseHex(value, 160)
      case "sha256" :: value :: Nil  => parseHex(value, 256)
      case "sha384" :: value :: Nil  => parseHex(value, 384)
      case "sha512" :: value :: Nil  => parseHex(value, 512)
      case _                         => throw IllegalArgumentException(s"unexpected digest: $s")

  private def parseHex(value: String, expectedBytes: Int): Array[Byte] =
    val bs = Hash.fromHex(value)
    require(bs.length == expectedBytes / 8, s"expected $expectedBytes, but found a digest $value")
    bs

  private def toHexString(bytes: Array[Byte]): String =
    val sb = new StringBuilder
    for b <- bytes do sb.append(f"${b & 0xff}%02x")
    sb.toString

  private def longsToBytes(longs: Array[Long]): Array[Byte] =
    val buffer = ByteBuffer.allocate(longs.length * java.lang.Long.BYTES)
    for l <- longs do buffer.putLong(l)
    buffer.array()

  given IsoString[Digest] = IsoString.iso(x => x, s => s)
end Digest
