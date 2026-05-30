package sbt.internal.util

import java.util.concurrent.{ ThreadLocalRandom, TimeUnit }
import net.openhft.hashing.LongHashFunction
import org.openjdk.jmh.annotations.*
import pt.kcry.blake3.Blake3
import sbt.util.Digest
import sbt.internal.util.hashing.Hashing
import scala.util.hashing.MurmurHash3

@State(Scope.Benchmark)
abstract class AbstractHashBenchmark:
  def hash(buf: Array[Byte]): String

  val buf: Array[Byte] = new Array[Byte](2048)
  ThreadLocalRandom.current().nextBytes(buf)

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def hashByteArray: Unit =
    hash(buf)
end AbstractHashBenchmark

class XXHash64HashBenchmark extends AbstractHashBenchmark:
  override def hash(buf: Array[Byte]): String =
    val h = Hashing.xxhash64
    val hash = h.hash(buf, 0, buf.size, 0)
    java.lang.Long.toHexString(hash)

class WyHash64HashBenchmark extends AbstractHashBenchmark:
  override def hash(buf: Array[Byte]): String =
    val h = Hashing.wyhash64
    val hash = h.hash(buf, 0, buf.size, 0)
    java.lang.Long.toHexString(hash)

class FarmHashHashBenchmark extends AbstractHashBenchmark:
  override def hash(buf: Array[Byte]): String =
    val hash = LongHashFunction.farmNa().hashBytes(buf)
    java.lang.Long.toHexString(hash)

class MurmurHash32HashBenchmark extends AbstractHashBenchmark:
  override def hash(buf: Array[Byte]): String =
    val lo = MurmurHash3.bytesHash(buf, 0x85ebca6b)
    val hash = lo.toLong & 0xffffffffL
    java.lang.Long.toHexString(hash)

class MurmurHash64HashBenchmark extends AbstractHashBenchmark:
  override def hash(buf: Array[Byte]): String =
    val hi = MurmurHash3.bytesHash(buf, 0x9747b28c)
    val lo = MurmurHash3.bytesHash(buf, 0x85ebca6b)
    val hash = (hi.toLong << 32) | (lo.toLong & 0xffffffffL)
    java.lang.Long.toHexString(hash)

class Md5HashBenchmark extends AbstractHashBenchmark:
  override def hash(buf: Array[Byte]): String =
    Digest.md5Hash(buf).toString

class Sha256HashBenchmark extends AbstractHashBenchmark:
  override def hash(buf: Array[Byte]): String =
    Digest.sha256Hash(buf).toString

class Blake3HashBenchmark extends AbstractHashBenchmark:
  override def hash(buf: Array[Byte]): String =
    Blake3.hex(buf, 64)
