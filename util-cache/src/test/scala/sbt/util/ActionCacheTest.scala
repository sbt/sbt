package sbt.util

import sbt.internal.util.StringVirtualFile1
import sbt.io.IO
import verify.BasicTestSuite
import java.nio.file.Paths
import xsbti.VirtualFile

object ActionCacheTest extends BasicTestSuite:
  val tags = CacheLevelTag.all.toList

  test("Disk cache can hold a blob"):
    withDiskCache(testHoldBlob)

  def testHoldBlob(cache: ActionCacheStore): Unit =
    val in = StringVirtualFile1("a.txt", "foo")
    val hashRefs = cache.putBlobs(in :: Nil)
    assert(hashRefs.size == 1)
    IO.withTemporaryDirectory: tempDir =>
      val actual = cache.syncBlobs(hashRefs, tempDir.toPath()).head
      assert(actual.getFileName().toString() == "a.txt")

  test("In-memory cache can hold action value"):
    withInMemoryCache(testActionCacheBasic)

  test("Disk cache can hold action value"):
    withDiskCache(testActionCacheBasic)

  def testActionCacheBasic(cache: ActionCacheStore): Unit =
    import sjsonnew.BasicJsonProtocol.*
    var called = 0
    val action: ((Int, Int)) => (Int, Seq[VirtualFile]) = { case (a, b) =>
      called += 1
      (a + b, Nil)
    }
    IO.withTemporaryDirectory: (tempDir) =>
      val config = BuildWideCacheConfiguration(cache, tempDir.toPath())
      val v1 =
        ActionCache.cache[(Int, Int), Int]((1, 1), Digest.zero, Digest.zero, tags)(action)(config)
      assert(v1.value == 2)
      val v2 =
        ActionCache.cache[(Int, Int), Int]((1, 1), Digest.zero, Digest.zero, tags)(action)(config)
      assert(v2.value == 2)
      // check that the action has been invoked only once
      assert(called == 1)

  test("Disk cache can hold action value with blob"):
    withDiskCache(testActionCacheWithBlob)

  def testActionCacheWithBlob(cache: ActionCacheStore): Unit =
    import sjsonnew.BasicJsonProtocol.*
    var called = 0
    val action: ((Int, Int)) => (Int, Seq[VirtualFile]) = { case (a, b) =>
      called += 1
      val out = StringVirtualFile1("a.txt", (a + b).toString)
      (a + b, Seq(out))
    }
    IO.withTemporaryDirectory: (tempDir) =>
      val config = BuildWideCacheConfiguration(cache, tempDir.toPath())
      val v1 =
        ActionCache.cache[(Int, Int), Int]((1, 1), Digest.zero, Digest.zero, tags)(action)(config)
      assert(v1.value == 2)
      // ActionResult only contains the reference to the files.
      // To retrieve them, separately call readBlobs or syncBlobs.
      val files1 = cache.syncBlobs(v1.outputFiles, tempDir.toPath())
      val file1 = files1(0)
      assert(file1.toFile().exists())
      val content = IO.read(file1.toFile())
      assert(content == "2")

      val v2 =
        ActionCache.cache[(Int, Int), Int]((1, 1), Digest.zero, Digest.zero, tags)(action)(config)
      assert(v2.value == 2)
      // check that the action has been invoked only once
      assert(called == 1)

  def withInMemoryCache(f: InMemoryActionCacheStore => Unit): Unit =
    val cache = InMemoryActionCacheStore()
    f(cache)

  def withDiskCache(f: DiskActionCacheStore => Unit): Unit =
    IO.withTemporaryDirectory(
      { tempDir0 =>
        val tempDir = tempDir0.toPath
        val cache = DiskActionCacheStore(tempDir)
        f(cache)
      },
      keepDirectory = false
    )
end ActionCacheTest
