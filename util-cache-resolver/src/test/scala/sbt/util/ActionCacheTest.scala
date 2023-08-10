package sbt.util

import sbt.internal.util.StringVirtualFile1
import verify.BasicTestSuite

object ActionCacheTest extends BasicTestSuite:
  test("In-memory cache") {
    val cache = InMemoryActionCacheStore()
    val in = StringVirtualFile1("a.txt", "foo")
    val hashRefs = cache.writeBlobs(in :: Nil)
    assert(hashRefs.size == 1)
    val actual = cache.readBlobs(hashRefs).head
    assert(actual.id == "a.txt")
  }
end ActionCacheTest
