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

  // Regression test: header values may legitimately contain '=' -- Basic auth credentials
  // end in base64 padding. Splitting on every '=' silently truncated the value, so the
  // server rejected the credential with UNAUTHENTICATED while the build still succeeded,
  // leaving the cache permanently empty with no error reported.
  test("header values retain '=' such as base64 padding"):
    val twoPad = GrpcActionCacheStore.AuthCallCredentials(List("authorization=Basic dXNlcjpwdw=="))
    val (key, value) = twoPad.pairs.head
    assert(key.name == "authorization")
    assert(value == "Basic dXNlcjpwdw==")

    val onePad = GrpcActionCacheStore.AuthCallCredentials(List("authorization=Basic dXNlcjpwdzE="))
    assert(onePad.pairs.head._2 == "Basic dXNlcjpwdzE=")

    // An interior '=' is part of the value, not a second separator.
    val interior = GrpcActionCacheStore.AuthCallCredentials(List("x-api-key=ab=cd"))
    assert(interior.pairs.head._2 == "ab=cd")

    // No '=' at all remains an error.
    intercept[RuntimeException]:
      GrpcActionCacheStore.AuthCallCredentials(List("bogus")).pairs

  private def newStore(): GrpcActionCacheStore =
    val base = Files.createTempDirectory("grpc-action-cache-test")
    val disk = DiskActionCacheStore(base, PlainVirtualFileConverter.converter)
    // A plaintext URI is enough to build the stubs; no connection is opened by reading
    // CallOptions, so no server is required.
    GrpcActionCacheStore(new URI("grpc://localhost:1"), None, None, None, Nil, disk)
end GrpcActionCacheStoreTest
