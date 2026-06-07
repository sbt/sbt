package sbt
package internal

object GrpcActionCacheStoreTest extends verify.BasicTestSuite:
  test("chunkBytes"):
    val actual = GrpcActionCacheStore.chunkBytes(0L)
    assert(actual == Nil)

    val actual2 = GrpcActionCacheStore.chunkBytes(1L)
    assert(actual2 == List(1L))

    val meg = 1024L * 1024L
    val actual3 = GrpcActionCacheStore.chunkBytes(meg)
    assert(actual3 == List(meg))

    val actual4 = GrpcActionCacheStore.chunkBytes(meg + 1)
    assert(actual4 == List(meg, 1L))
end GrpcActionCacheStoreTest
