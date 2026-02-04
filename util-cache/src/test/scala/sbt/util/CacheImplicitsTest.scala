package sbt.util

import java.io.InputStream
import java.nio.file.{ NoSuchFileException, Path, Paths }
import verify.BasicTestSuite
import xsbti.{ BasicVirtualFileRef, PathBasedFile }

object CacheImplicitsTest extends BasicTestSuite:
  test("hashedVirtualFileRefToStr handles non-existent PathBasedFile"):
    val nonExistentPath = Paths.get("/tmp/does-not-exist-8687.jar")
    val ref = TestPathBasedFile(nonExistentPath)
    val result = CacheImplicits.hashedVirtualFileRefToStr(ref)
    assert(result.contains(">"))
    assert(result.contains("/"))
    val parsed = CacheImplicits.strToHashedVirtualFileRef(result)
    assert(parsed.id == ref.id)
    assert(parsed.sizeBytes == 0L)
end CacheImplicitsTest

class TestPathBasedFile(p: Path) extends BasicVirtualFileRef(p.toString) with PathBasedFile:
  override def toPath: Path = p
  override def input: InputStream =
    throw NoSuchFileException(p.toString)
  override def contentHash: Long =
    throw NoSuchFileException(p.toString)
  override def contentHashStr: String =
    throw NoSuchFileException(p.toString)
  override def sizeBytes: Long =
    throw NoSuchFileException(p.toString)
end TestPathBasedFile
