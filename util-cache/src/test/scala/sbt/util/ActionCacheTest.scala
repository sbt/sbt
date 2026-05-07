package sbt.util

import java.io.{ IOException, InputStream }
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path, Paths }
import java.util.Optional
import java.util.concurrent.{ CyclicBarrier, ExecutorService, Executors, TimeUnit }

import sbt.internal.util.CacheEventLog
import sbt.internal.util.StringVirtualFile1
import sbt.io.IO
import sbt.io.syntax.*
import verify.BasicTestSuite
import xsbti.{
  CompileFailed,
  FileConverter,
  HashedVirtualFileRef,
  Problem,
  Position,
  Severity,
  VirtualFile,
  VirtualFileRef,
}

import ActionCache.InternalActionResult

object ActionCacheTest extends BasicTestSuite:
  val tags = CacheLevelTag.all.toList

  test("Disk cache can hold a blob"):
    withDiskCache(testHoldBlob)

  test("Disk cache rejects truncated blobs"):
    withDiskCache: cache =>
      val blob = StringVirtualFile1("a.txt", "hello")
      val digest = Digest(blob)
      val ref: HashedVirtualFileRef = blob
      val casFile = cache.toCasFile(digest)
      Files.writeString(casFile, "he", StandardCharsets.UTF_8)

      assert(cache.findBlobs(Seq(ref)).isEmpty)
      cache.putBlobs(Seq(blob))
      assert(cache.findBlobs(Seq(ref)) == Seq(ref))
      assert(Files.readString(casFile, StandardCharsets.UTF_8) == "hello")

  test("Disk cache removes staged blobs when writes fail"):
    withDiskCache: cache =>
      val blob = StringVirtualFile1("a.txt", "hello")
      val digest = Digest(blob)
      val casFile = cache.toCasFile(digest)
      try
        cache.putBlob(FailingInputStream("hello".getBytes(StandardCharsets.UTF_8), 2), digest)
        assert(false, "expected blob write to fail")
      catch case _: IOException => ()

      assert(!Files.exists(casFile))
      assert(Files.list(cache.casBase).toArray.isEmpty)

  def testHoldBlob(cache: ActionCacheStore): Unit =
    IO.withTemporaryDirectory: tempDir =>
      val in = StringVirtualFile1(s"$tempDir/a.txt", "foo")
      val hashRefs = cache.putBlobs(in :: Nil)
      assert(hashRefs.size == 1)
      val actual = cache.syncBlobs(hashRefs, tempDir.toPath()).head
      assert(actual.getFileName().toString() == "a.txt")

  final class FailingInputStream(bytes: Array[Byte], failAt: Int) extends InputStream:
    private var index = 0
    override def read(): Int =
      if index == failAt then throw IOException("simulated interrupted write")
      else if index >= bytes.length then -1
      else
        val b = bytes(index) & 0xff
        index += 1
        b

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

  test("Cache falls back to recompute when syncBlobs throws FileNotFoundException"):
    withDiskCache(testSyncBlobsThrowsFallback)

  def testSyncBlobsThrowsFallback(underlying: DiskActionCacheStore): Unit =
    import sjsonnew.BasicJsonProtocol.*
    var called = 0
    val action: ((Int, Int)) => InternalActionResult[Int] = { (a, b) =>
      called += 1
      InternalActionResult(a + b, Nil)
    }
    class ThrowingSyncStore extends AbstractActionCacheStore:
      override def storeName: String = "throwing-sync"
      override def get(request: GetActionResultRequest): Either[Throwable, ActionResult] =
        underlying.get(request)
      override def put(request: UpdateActionResultRequest): Either[Throwable, ActionResult] =
        underlying.put(request)
      override def putBlobs(blobs: Seq[VirtualFile]): Seq[HashedVirtualFileRef] =
        underlying.putBlobs(blobs)
      override def syncBlobs(refs: Seq[HashedVirtualFileRef], outputDirectory: Path): Seq[Path] =
        throw new java.io.FileNotFoundException("simulated missing CAS entry")
      override def findBlobs(refs: Seq[HashedVirtualFileRef]): Seq[HashedVirtualFileRef] =
        underlying.findBlobs(refs)
    IO.withTemporaryDirectory: tempDir =>
      val config = getCacheConfig(ThrowingSyncStore(), tempDir)
      val v1 = ActionCache.cache((1, 1), Digest.zero, Digest.zero, tags, config)(action)
      assert(v1 == 2)
      assert(called == 1)
      val v2 = ActionCache.cache((1, 1), Digest.zero, Digest.zero, tags, config)(action)
      assert(v2 == 2)
      assert(called == 2)

  test(
    "readFromSymlink fast path falls back to recompute when syncBlobs throws FileNotFoundException"
  ):
    IO.withTemporaryDirectory: cacheDir =>
      IO.withTemporaryDirectory: outputDir =>
        testReadFromSymlinkFallback(cacheDir, outputDir)

  def testReadFromSymlinkFallback(cacheDir: File, outputDir: File): Unit =
    import sjsonnew.BasicJsonProtocol.*
    val absConverter: FileConverter = new FileConverter:
      override def toPath(ref: VirtualFileRef): Path = outputDir.toPath.resolve(ref.id)
      override def toVirtualFile(path: Path): VirtualFile =
        val content = if Files.isRegularFile(path) then new String(Files.readAllBytes(path)) else ""
        StringVirtualFile1(path.toString, content)
    val diskCache = DiskActionCacheStore(cacheDir.toPath, absConverter)
    var called = 0
    val action: Unit => InternalActionResult[Int] = { _ =>
      called += 1
      InternalActionResult(42, Nil)
    }
    val logger = new Logger:
      override def trace(t: => Throwable): Unit = ()
      override def success(message: => String): Unit = ()
      override def log(level: Level.Value, message: => String): Unit = ()
    val config1 =
      BuildWideCacheConfiguration(
        diskCache,
        outputDir.toPath,
        absConverter,
        logger,
        CacheEventLog()
      )
    val v1 = ActionCache.cache((), Digest.zero, Digest.zero, tags, config1)(action)
    assert(v1 == 42)
    assert(called == 1)
    class ThrowingSyncStore extends AbstractActionCacheStore:
      override def storeName: String = "throwing-sync"
      override def get(request: GetActionResultRequest): Either[Throwable, ActionResult] =
        diskCache.get(request)
      override def put(request: UpdateActionResultRequest): Either[Throwable, ActionResult] =
        diskCache.put(request)
      override def putBlobs(blobs: Seq[VirtualFile]): Seq[HashedVirtualFileRef] =
        diskCache.putBlobs(blobs)
      override def syncBlobs(refs: Seq[HashedVirtualFileRef], outputDirectory: Path): Seq[Path] =
        throw new java.io.FileNotFoundException("simulated missing CAS entry")
      override def findBlobs(refs: Seq[HashedVirtualFileRef]): Seq[HashedVirtualFileRef] =
        diskCache.findBlobs(refs)
    val config2 =
      BuildWideCacheConfiguration(
        ThrowingSyncStore(),
        outputDir.toPath,
        absConverter,
        logger,
        CacheEventLog()
      )
    val v2 = ActionCache.cache((), Digest.zero, Digest.zero, tags, config2)(action)
    assert(v2 == 42)
    assert(called == 2)

  test("packageDirectory is safe when many threads package the same directory concurrently"):
    IO.withTemporaryDirectory: tmp =>
      val root = tmp.toPath
      val classesDir = root.resolve("classes")
      Files.createDirectories(classesDir)
      Files.writeString(classesDir.resolve("A.class"), "compiled")
      val classesPathStr = classesDir.toString
      val dirRef = VirtualFileRef.of(classesPathStr)
      val conv = new FileConverter:
        override def toPath(ref: VirtualFileRef): Path = Paths.get(ref.id)
        override def toVirtualFile(path: Path): VirtualFile =
          val content =
            if Files.isRegularFile(path) then
              new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
            else ""
          StringVirtualFile1(path.toString, content)
      val threadCount = 64
      val barrier = new CyclicBarrier(threadCount)
      val pool: ExecutorService = Executors.newFixedThreadPool(threadCount)
      try
        val tasks =
          for _ <- 1 to threadCount yield pool.submit: () =>
            barrier.await(30, TimeUnit.SECONDS)
            ActionCache.packageDirectory(dirRef, conv, root)
        tasks.foreach(_.get(60, TimeUnit.SECONDS))
        val zipPath = Paths.get(classesPathStr + ActionCache.dirZipExt)
        assert(Files.isRegularFile(zipPath))
        assert(Files.size(zipPath) > 0L)
      finally pool.shutdown()

  test("Changing cacheVersion invalidates the cache"):
    withDiskCache(testCacheVersionInvalidation)

  def testCacheVersionInvalidation(cache: ActionCacheStore): Unit =
    import sjsonnew.BasicJsonProtocol.*
    var called = 0
    val action: ((Int, Int)) => InternalActionResult[Int] = { (a, b) =>
      called += 1
      InternalActionResult(a + b, Nil)
    }
    IO.withTemporaryDirectory: tempDir =>
      val config0 = getCacheConfig(cache, tempDir)
      // First call: computes the result
      val v1 = ActionCache.cache((1, 1), Digest.zero, Digest.zero, tags, config0)(action)
      assert(v1 == 2)
      assert(called == 1)
      // Second call with same config: hits cache
      val v2 = ActionCache.cache((1, 1), Digest.zero, Digest.zero, tags, config0)(action)
      assert(v2 == 2)
      assert(called == 1)
      // Third call with different cacheVersion: cache miss, recomputes
      val config1 = getCacheConfig(cache, tempDir, cacheVersion = 1L)
      val v3 = ActionCache.cache((1, 1), Digest.zero, Digest.zero, tags, config1)(action)
      assert(v3 == 2)
      assert(called == 2)
      // Fourth call with same cacheVersion=1: hits cache again
      val v4 = ActionCache.cache((1, 1), Digest.zero, Digest.zero, tags, config1)(action)
      assert(v4 == 2)
      assert(called == 2)

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

  def getCacheConfig(
      cache: ActionCacheStore,
      outputDir: File,
      cacheVersion: Long = 0L,
  ): BuildWideCacheConfiguration =
    val logger = new Logger:
      override def trace(t: => Throwable): Unit = ()
      override def success(message: => String): Unit = ()
      override def log(level: Level.Value, message: => String): Unit = ()
    BuildWideCacheConfiguration(
      cache,
      outputDir.toPath(),
      fileConverter,
      logger,
      CacheEventLog(),
      CacheImplicits.defaultLocalDigestCacheByteSize,
      cacheVersion,
    )

  def fileConverter = new FileConverter:
    override def toPath(ref: VirtualFileRef): Path = Paths.get(ref.id)
    override def toVirtualFile(path: Path): VirtualFile =
      val content = if Files.isRegularFile(path) then new String(Files.readAllBytes(path)) else ""
      StringVirtualFile1(path.toString, content)
end ActionCacheTest
