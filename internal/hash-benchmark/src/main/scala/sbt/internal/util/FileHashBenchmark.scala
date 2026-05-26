package sbt.internal.util

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.*
import sbt.util.Digest

import java.nio.file.{ Path as NioPath }
import sbt.io.IO
import sbt.io.syntax.*

@State(Scope.Benchmark)
abstract class AbstractFileHashBenchmark:
  val tempDir = IO.createTemporaryDirectory
  val temp = tempDir / "test.txt"
  val buf: Array[Byte] = Array.fill[Byte](1024)(0.toByte)
  for i <- 0 until 1024 do IO.append(temp, buf)

  def hash(path: NioPath): String

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def hashFile: Unit =
    hash(temp.toPath())
end AbstractFileHashBenchmark

class XXHash64FileHashBenchmark extends AbstractFileHashBenchmark:
  override def hash(path: NioPath): String =
    Digest.xx64Hash(path).toString

class WyHash64FileHashBenchmark extends AbstractFileHashBenchmark:
  override def hash(path: NioPath): String =
    Digest.wy64Hash(path).toString

class ImoXXHash64FileHashBenchmark extends AbstractFileHashBenchmark:
  override def hash(path: NioPath): String =
    Digest.imoxx64Hash(path).toString

class ImoWyHash64FileHashBenchmark extends AbstractFileHashBenchmark:
  override def hash(path: NioPath): String =
    Digest.imowy64Hash(path).toString

class Sha1FileHashBenchmark extends AbstractFileHashBenchmark:
  override def hash(path: NioPath): String =
    Digest.sha1Hash(path).toString

class Sha256FileHashBenchmark extends AbstractFileHashBenchmark:
  override def hash(path: NioPath): String =
    Digest.sha256Hash(path).toString
