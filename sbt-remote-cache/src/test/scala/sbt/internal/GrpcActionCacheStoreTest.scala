package sbt
package internal

import build.bazel.remote.execution.v2.{
  BatchReadBlobsResponse,
  BatchUpdateBlobsResponse,
  Digest as XDigest
}
import com.eed3si9n.remoteapis.shaded.com.google.rpc.Status as RpcStatus
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import sbt.internal.inc.PlainVirtualFileConverter
import sbt.internal.util.StringVirtualFile1
import sbt.util.{ Digest, DiskActionCacheStore }
import scala.concurrent.duration.*
import xsbti.VirtualFile

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
  // requestTimeout after first use, after which every later blob transfer fails with
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

  // Regression test: the unary RPCs need the deadline too, not just the ByteStream transfers.
  // Without one, a cache that accepts the connection and then stops answering blocks the task
  // for as long as the OS keeps the socket, since store.get and store.put block in the task body.
  test("unary stubs carry a per-call deadline"):
    val store = newStore()
    assert(store.acStub.getCallOptions.getDeadline == null)
    assert(store.casStub.getCallOptions.getDeadline == null)

    val ac1 = store.acStubWithDeadline.getCallOptions.getDeadline
    val ac2 = store.acStubWithDeadline.getCallOptions.getDeadline
    assert(ac1 != null)
    assert(!ac1.eq(ac2))

    val cas1 = store.casStubWithDeadline.getCallOptions.getDeadline
    val cas2 = store.casStubWithDeadline.getCallOptions.getDeadline
    assert(cas1 != null)
    assert(!cas1.eq(cas2))

  // A metadata RPC carries no payload, so it must not wait as long as a blob transfer:
  // otherwise a hung cache costs the full transfer timeout on every cached task.
  test("metadata RPCs get a shorter deadline than blob transfers"):
    val store = newStore()
    val metadata = store.casStubWithDeadline.getCallOptions.getDeadline
    val transfer = store.casTransferStubWithDeadline.getCallOptions.getDeadline
    assert(metadata.timeRemaining(TimeUnit.SECONDS) <= 10)
    assert(transfer.timeRemaining(TimeUnit.SECONDS) > 10)

    // A requestTimeout below the cap is honored as is, not raised to it.
    val shortStore = newStore(2.seconds)
    val shortMetadata = shortStore.casStubWithDeadline.getCallOptions.getDeadline
    assert(shortMetadata.timeRemaining(TimeUnit.MILLISECONDS) <= 2000)

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

  test("A batch read with a failed blob fails instead of returning no paths"):
    def response(hash: String, code: Int) =
      BatchReadBlobsResponse.Response
        .newBuilder()
        .setDigest(XDigest.newBuilder().setHash(hash).setSizeBytes(1))
        .setStatus(RpcStatus.newBuilder().setCode(code))
        .build()
    GrpcActionCacheStore.requireAllRead(List(response("a", 0), response("b", 0)))
    val message =
      try
        GrpcActionCacheStore.requireAllRead(List(response("a", 0), response("b", 5)))
        "no exception"
      catch case e: IOException => e.getMessage
    assert(message.contains("failed to read 1 of 2 blobs: b (code 5"), message)

  // A batch update reports its failures per blob inside a successful call, so keying the
  // responses by digest alone counted a declined blob as stored, and the action result then
  // referred to a blob the CAS does not have.
  test("A batch update records only the blobs the server stored"):
    val a = StringVirtualFile1("a.txt", "a")
    val b = StringVirtualFile1("b.txt", "b")
    def response(blob: VirtualFile, code: Int) =
      BatchUpdateBlobsResponse.Response
        .newBuilder()
        .setDigest(BazelRemote.toXDigest(Digest(blob)))
        .setStatus(RpcStatus.newBuilder().setCode(code))
        .build()
    val both = GrpcActionCacheStore.storedBlobs(Seq(a, b), Seq(response(a, 0), response(b, 0)))
    assert(both.map(_.id) == Seq("a.txt", "b.txt"))
    // 7 is PERMISSION_DENIED in google.rpc.Code.
    val one = GrpcActionCacheStore.storedBlobs(Seq(a, b), Seq(response(a, 0), response(b, 7)))
    assert(one.map(_.id) == Seq("a.txt"))

  private def newStore(requestTimeout: FiniteDuration = 60.seconds): GrpcActionCacheStore =
    val base = Files.createTempDirectory("grpc-action-cache-test")
    val disk = DiskActionCacheStore(base, PlainVirtualFileConverter.converter)
    // A plaintext URI is enough to build the stubs; no connection is opened by reading
    // CallOptions, so no server is required.
    GrpcActionCacheStore(new URI("grpc://localhost:1"), None, None, None, Nil, disk, requestTimeout)
end GrpcActionCacheStoreTest
