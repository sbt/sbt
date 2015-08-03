package sbt

import java.io.File
import java.util.Arrays
import org.scalacheck._
import Prop._
import Arbitrary.arbLong

object CopySpec extends Properties("Copy") {
  // set to 0.25 GB by default for success on most systems without running out of space.
  // when modifying IO.copyFile, verify against 1 GB or higher, preferably > 4 GB
  final val MaxFileSizeBits = 28
  final val BufferSize = 1 * 1024 * 1024

  val randomSize = Gen.choose(0, MaxFileSizeBits).map(1L << _)
  val pow2Size = (0 to (MaxFileSizeBits - 1)).toList.map(1L << _)
  val derivedSize = pow2Size.map(_ - 1) ::: pow2Size.map(_ + 1) ::: pow2Size

  val fileSizeGen: Gen[Long] =
    Gen.frequency(
      80 -> Gen.oneOf(derivedSize),
      8 -> randomSize,
      1 -> Gen.value(0)
    )

  property("same contents") = forAll(fileSizeGen, arbLong.arbitrary) { (size: Long, seed: Long) =>
    IO.withTemporaryDirectory { dir =>
      val f1 = new File(dir, "source")
      val f2 = new File(dir, "dest")
      generate(seed = seed, size = size, file = f1)
      IO.copyFile(f1, f2)
      checkContentsSame(f1, f2)
      true
    }
  }

  def generate(seed: Long, size: Long, file: File): Unit = {
    val rnd = new java.util.Random(seed)

    val buffer = new Array[Byte](BufferSize)
    def loop(offset: Long): Unit = {
      val len = math.min(size - offset, BufferSize)
      if (len > 0) {
        rnd.nextBytes(buffer)
        IO.append(file, buffer)
        loop(offset + len)
      }
    }
    if (size == 0L) IO.touch(file) else loop(0)
  }
  def checkContentsSame(f1: File, f2: File): Unit = {
    val len = f1.length
    assert(len == f2.length, "File lengths differ: " + (len, f2.length).toString + " for " + (f1, f2).toString)
    Using.fileInputStream(f1) { in1 =>
      Using.fileInputStream(f2) { in2 =>
        val buffer1 = new Array[Byte](BufferSize)
        val buffer2 = new Array[Byte](BufferSize)
        def loop(offset: Long): Unit = if (offset < len) {
          val read1 = in1.read(buffer1)
          val read2 = in2.read(buffer2)
          assert(read1 == read2, "Read " + (read1, read2).toString + " bytes from " + (f1, f2).toString)
          assert(Arrays.equals(buffer1, buffer2), "Contents differed.")
          loop(offset + read1)
        }
        loop(0)
      }
    }
  }
}
