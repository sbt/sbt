package sbt.util

import sbt.internal.util.CacheEventLog
import sbt.internal.util.StringVirtualFile1
import sbt.io.IO
import sbt.io.syntax.*
import verify.BasicTestSuite
import xsbti.VirtualFile
import xsbti.FileConverter
import xsbti.VirtualFileRef
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files

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
      val config = getCacheConfig(cache, tempDir)
      val v1 =
        ActionCache.cache((1, 1), Digest.zero, Digest.zero, tags)(action)(config)
      assert(v1 == 2)
      val v2 =
        ActionCache.cache((1, 1), Digest.zero, Digest.zero, tags)(action)(config)
      assert(v2 == 2)
      // check that the action has been invoked only once
      assert(called == 1)

  test("Disk cache can hold action value with blob"):
    withDiskCache(testActionCacheWithBlob)

  def testActionCacheWithBlob(cache: ActionCacheStore): Unit =
    import sjsonnew.BasicJsonProtocol.*
    IO.withTemporaryDirectory: (tempDir) =>
      var called = 0
      val action: ((Int, Int)) => (Int, Seq[VirtualFile]) = { case (a, b) =>
        called += 1
        val out = StringVirtualFile1(s"$tempDir/a.txt", (a + b).toString)
        (a + b, Seq(out))
      }
      val config = getCacheConfig(cache, tempDir)
      val v1 =
        ActionCache.cache((1, 1), Digest.zero, Digest.zero, tags)(action)(config)
      assert(v1 == 2)
      // ActionResult only contains the reference to the files.
      // To retrieve them, separately call readBlobs or syncBlobs.
      val file1 = tempDir / "a.txt"
      assert(file1.exists())
      val content = IO.read(file1)
      assert(content == "2")

      val v2 =
        ActionCache.cache((1, 1), Digest.zero, Digest.zero, tags)(action)(config)
      assert(v2 == 2)
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

  def getCacheConfig(cache: ActionCacheStore, outputDir: File): BuildWideCacheConfiguration =
    val logger = new Logger:
      override def trace(t: => Throwable): Unit = ()
      override def success(message: => String): Unit = ()
      override def log(level: Level.Value, message: => String): Unit = ()
    BuildWideCacheConfiguration(cache, outputDir.toPath(), fileConverter, logger, CacheEventLog())

  def fileConverter = new FileConverter:
    override def toPath(ref: VirtualFileRef): Path = Paths.get(ref.id)
    override def toVirtualFile(path: Path): VirtualFile =
      val content = if Files.isRegularFile(path) then new String(Files.readAllBytes(path)) else ""
      StringVirtualFile1(path.toString, content)
end ActionCacheTest
