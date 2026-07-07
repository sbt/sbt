package sbt
package internal

import java.net.URI
import java.nio.file.Files
import sbt.internal.inc.PlainVirtualFileConverter
import sbt.util.DiskActionCacheStore

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

  // Regression test: the ByteStream deadline must be applied per RPC, not baked into the
  // memoized stub. A deadline on the (session-lived) stub is absolute and expires
  // remoteTimeoutInSec after first use, after which every later blob transfer fails with
  // DEADLINE_EXCEEDED for the rest of the sbt server's life.
  test("byteStream deadline is per-call, not on the memoized stub"):
    val store = newStore()
    assert(store.byteStreamStub.getCallOptions.getDeadline == null)

    val deadline1 = store.byteStreamStubWithDeadline.getCallOptions.getDeadline
    val deadline2 = store.byteStreamStubWithDeadline.getCallOptions.getDeadline
    assert(deadline1 != null)
    assert(deadline2 != null)
    // Distinct Deadline instances derived at call time, not a single shared frozen one.
    assert(!deadline1.eq(deadline2))

  private def newStore(): GrpcActionCacheStore =
    val base = Files.createTempDirectory("grpc-action-cache-test")
    val disk = DiskActionCacheStore(base, PlainVirtualFileConverter.converter)
    // A plaintext URI is enough to build the stubs; no connection is opened by reading
    // CallOptions, so no server is required.
    GrpcActionCacheStore(new URI("grpc://localhost:1"), None, None, None, Nil, disk)
end GrpcActionCacheStoreTest
