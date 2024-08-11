package sbt.util

import sjsonnew.IsoString
import sbt.io.Hash
import xsbti.HashedVirtualFileRef
import java.io.{ BufferedInputStream, InputStream }
import java.nio.ByteBuffer
import java.nio.file.{ Files, Path }
import java.security.{ DigestInputStream, MessageDigest }

opaque type Digest = String

object Digest:
  private[sbt] val Murmur3 = "murmur3"
  private[sbt] val Md5 = "md5"
  private[sbt] val Sha1 = "sha1"
  private[sbt] val Sha256 = "sha256"
  private[sbt] val Sha384 = "sha384"
  private[sbt] val Sha512 = "sha512"

  extension (d: Digest)
    def contentHashStr: String =
      val tokens = parse(d)
      s"${tokens._1}-${tokens._2}"
    def algo: String = parse(d)._1
    def toBytes: Array[Byte] = parse(d)._4
    def sizeBytes: Long = parse(d)._3

  def apply(s: String): Digest =
    validateString(s)
    s

  def apply(algo: String, digest: Array[Byte], sizeBytes: Long): Digest =
    algo + "-" + toHexString(digest) + "/" + sizeBytes.toString

  def apply(ref: HashedVirtualFileRef): Digest =
    apply(ref.contentHashStr() + "/" + ref.sizeBytes.toString)

  def apply(algo: String, path: Path): Digest =
    val input = Files.newInputStream(path)
    try
      apply(algo, hashBytes(algo, input), Files.size(path))
    finally
      input.close()

  // used to wrap a Long value as a fake Digest, which will
  // later be hashed using sha256 anyway.
  def dummy(value: Long): Digest =
    apply(Murmur3, longsToBytes(Array(0L, value)), 0)

  lazy val zero: Digest = dummy(0L)

  def sha256Hash(path: Path): Digest = apply(Sha256, path)

  def sha256Hash(bytes: Array[Byte]): Digest =
    apply(Sha256, hashBytes(Sha256, bytes), bytes.length)

  def sha256Hash(longs: Array[Long]): Digest =
    val bytes = hashBytes(Sha256, longs)
    apply(Sha256, bytes, bytes.length)

  def sha256Hash(digests: Digest*): Digest =
    sha256Hash(digests.toSeq.map(_.toBytes).flatten.toArray[Byte])

  // first check the file size, then the hash
  def sameDigest(path: Path, digest: Digest): Boolean =
    if Files.size(path) != digest.sizeBytes then false
    else Digest(digest.algo, path) == digest

  private def hashBytes(algo: String, bytes: Array[Byte]): Array[Byte] =
    val digest = MessageDigest.getInstance(jvmAlgo(algo))
    digest.digest(bytes)

  private def hashBytes(algo: String, longs: Array[Long]): Array[Byte] =
    hashBytes(algo, longsToBytes(longs))

  private def hashBytes(algo: String, input: InputStream): Array[Byte] =
    val BufferSize = 8192
    val bis = BufferedInputStream(input)
    val digest = MessageDigest.getInstance(jvmAlgo(algo))
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

  private def parse(s: String): (String, String, Long, Array[Byte]) =
    val tokens = s.split("-").toList
    tokens match
      case head :: rest :: Nil =>
        val subtokens = head :: rest.split("/").toList
        subtokens match
          case (a @ Murmur3) :: value :: sizeBytes :: Nil =>
            (a, value, sizeBytes.toLong, parseHex(value, 128))
          case (a @ Md5) :: value :: sizeBytes :: Nil =>
            (a, value, sizeBytes.toLong, parseHex(value, 128))
          case (a @ Sha1) :: value :: sizeBytes :: Nil =>
            (a, value, sizeBytes.toLong, parseHex(value, 160))
          case (a @ Sha256) :: value :: sizeBytes :: Nil =>
            (a, value, sizeBytes.toLong, parseHex(value, 256))
          case (a @ Sha384) :: value :: sizeBytes :: Nil =>
            (a, value, sizeBytes.toLong, parseHex(value, 384))
          case (a @ Sha512) :: value :: sizeBytes :: Nil =>
            (a, value, sizeBytes.toLong, parseHex(value, 512))
          case _ => throw IllegalArgumentException(s"unexpected digest: $s")
      case _ => throw IllegalArgumentException(s"unexpected digest: $s")

  private def jvmAlgo(algo: String): String =
    algo match
      case Md5    => "MD5"
      case Sha1   => "SHA-1"
      case Sha256 => "SHA-256"
      case Sha384 => "SHA-384"
      case Sha512 => "SHA-512"
      case a      => a

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
