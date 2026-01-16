package sbt.util

import sbt.internal.util.CacheEventLog
import sbt.internal.util.StringVirtualFile1
import sbt.io.IO
import sbt.io.syntax.*
import verify.BasicTestSuite
import xsbti.{
  CompileFailed,
  Problem,
  Position,
  Severity,
  VirtualFile,
  FileConverter,
  VirtualFileRef
}
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files
import java.util.Optional
import ActionCache.InternalActionResult

object ActionCacheTest extends BasicTestSuite:
  val tags = CacheLevelTag.all.toList

  test("Disk cache can hold a blob"):
    withDiskCache(testHoldBlob)

  def testHoldBlob(cache: ActionCacheStore): Unit =
    IO.withTemporaryDirectory: tempDir =>
      val in = StringVirtualFile1(s"$tempDir/a.txt", "foo")
      val hashRefs = cache.putBlobs(in :: Nil)
      assert(hashRefs.size == 1)
      val actual = cache.syncBlobs(hashRefs, tempDir.toPath()).head
      assert(actual.getFileName().toString() == "a.txt")

  test("In-memory cache can hold action value"):
    withInMemoryCache(testActionCacheBasic)

  test("Disk cache can hold action value"):
    withDiskCache(testActionCacheBasic)

  def testActionCacheBasic(cache: ActionCacheStore): Unit =
    import sjsonnew.BasicJsonProtocol.*
    var called = 0
    val action: ((Int, Int)) => InternalActionResult[Int] = { (a, b) =>
      called += 1
      InternalActionResult(a + b, Nil)
    }
    IO.withTemporaryDirectory: (tempDir) =>
      val config = getCacheConfig(cache, tempDir)
      val v1 =
        ActionCache.cache((1, 1), Digest.zero, Digest.zero, tags, config)(action)
      assert(v1 == 2)
      val v2 =
        ActionCache.cache((1, 1), Digest.zero, Digest.zero, tags, config)(action)
      assert(v2 == 2)
      // check that the action has been invoked only once
      assert(called == 1)

  test("Disk cache can hold action value with blob"):
    withDiskCache(testActionCacheWithBlob)

  def testActionCacheWithBlob(cache: ActionCacheStore): Unit =
    import sjsonnew.BasicJsonProtocol.*
    IO.withTemporaryDirectory: (tempDir) =>
      var called = 0
      val action: ((Int, Int)) => InternalActionResult[Int] = { (a, b) =>
        called += 1
        val out = StringVirtualFile1(s"$tempDir/a.txt", (a + b).toString)
        InternalActionResult(a + b, Seq(out))
      }
      val config = getCacheConfig(cache, tempDir)
      val v1 =
        ActionCache.cache((1, 1), Digest.zero, Digest.zero, tags, config)(action)
      assert(v1 == 2)
      // ActionResult only contains the reference to the files.
      // To retrieve them, separately call readBlobs or syncBlobs.
      val file1 = tempDir / "a.txt"
      assert(file1.exists())
      val content = IO.read(file1)
      assert(content == "2")

      val v2 =
        ActionCache.cache((1, 1), Digest.zero, Digest.zero, tags, config)(action)
      assert(v2 == 2)
      // check that the action has been invoked only once
      assert(called == 1)

  test("Disk cache can recover gracefully from invalid JSON"):
    withDiskCache(testActionCacheInvalidJson)

  test("Disk cache caches CompileFailed exceptions"):
    withDiskCache(testCachedCompileFailure)

  def testActionCacheInvalidJson(cache: DiskActionCacheStore): Unit =
    import sjsonnew.BasicJsonProtocol.*
    var called = 0
    val action: ((Int, Int)) => InternalActionResult[Int] = { (a, b) =>
      called += 1
      InternalActionResult(a + b, Nil)
    }
    IO.withTemporaryDirectory: tempDir =>
      val config = getCacheConfig(cache, tempDir)

      val v1 = ActionCache.cache((1, 1), Digest.zero, Digest.zero, tags, config)(action)
      assert(v1 == 2)

      val acFiles = cache.acBase.toFile.listFiles
      assert(acFiles.length == 1)
      IO.write(acFiles.head, "{")

      val v2 = ActionCache.cache((1, 1), Digest.zero, Digest.zero, tags, config)(action)
      assert(v2 == 2)
      // check that the action has been invoked twice
      assert(called == 2)

  def testCachedCompileFailure(cache: DiskActionCacheStore): Unit =
    import sjsonnew.BasicJsonProtocol.*
    var called = 0
    val testProblem = new Problem:
      override def category(): String = "Test"
      override def severity(): Severity = Severity.Error
      override def message(): String = "Test error message"
      override def position(): Position = new Position:
        override def line(): Optional[Integer] = Optional.of(42)
        override def lineContent(): String = "val x = 1"
        override def offset(): Optional[Integer] = Optional.empty()
        override def pointer(): Optional[Integer] = Optional.empty()
        override def pointerSpace(): Optional[String] = Optional.empty()
        override def sourcePath(): Optional[String] = Optional.of("/test/file.scala")
        override def sourceFile(): Optional[java.io.File] = Optional.empty()

    val testException = new CompileFailed:
      override def arguments(): Array[String] = Array.empty
      override def problems(): Array[Problem] = Array(testProblem)
      override def getMessage(): String = "Compilation failed"

    val action: ((Int, Int)) => InternalActionResult[Int] = { (a, b) =>
      called += 1
      throw testException
    }
    IO.withTemporaryDirectory: tempDir =>
      val config = getCacheConfig(cache, tempDir)

      // First call should throw and cache the failure
      var caught1: CompileFailed = null
      try
        ActionCache.cache((1, 1), Digest.zero, Digest.zero, tags, config)(action)
        assert(false, "Expected CompileFailed to be thrown")
      catch case e: CompileFailed => caught1 = e

      assert(caught1 != null)
      assert(called == 1)

      // Second call should throw cached failure without calling action again
      var caught2: CompileFailed = null
      try
        ActionCache.cache((1, 1), Digest.zero, Digest.zero, tags, config)(action)
        assert(false, "Expected CompileFailed to be thrown")
      catch case e: CompileFailed => caught2 = e

      assert(caught2 != null)
      // Action should NOT have been called again - failure was cached
      assert(called == 1)
      // Verify the cached exception has the same data
      assert(caught2.problems().length == 1)
      assert(caught2.problems()(0).message() == "Test error message")
      assert(caught2.getMessage() == "Compilation failed")

  def withInMemoryCache(f: InMemoryActionCacheStore => Unit): Unit =
    val cache = InMemoryActionCacheStore()
    f(cache)

  def withDiskCache(f: DiskActionCacheStore => Unit): Unit =
    IO.withTemporaryDirectory(
      { tempDir0 =>
        val tempDir = tempDir0.toPath
        val cache = DiskActionCacheStore(tempDir, fileConverter)
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
