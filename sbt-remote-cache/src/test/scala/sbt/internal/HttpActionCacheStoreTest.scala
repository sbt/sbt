package sbt
package internal

import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }
import java.security.MessageDigest
import sbt.internal.inc.PlainVirtualFileConverter
import sbt.internal.util.StringVirtualFile1
import sbt.io.IO
import sbt.util.{ Digest, DiskActionCacheStore, GetActionResultRequest, UpdateActionResultRequest }
import scala.concurrent.duration.*
import scala.util.Using
import xsbti.{ HashedVirtualFileRef, VirtualFile }

object HttpActionCacheStoreTest extends verify.BasicTestSuite:
  private val converter = PlainVirtualFileConverter.converter
  private val actionDigest = Digest.sha256Hash("action".getBytes(StandardCharsets.UTF_8))

  test("put, get, and sync round trip from a machine with an empty disk cache"):
    withRemote(): (remote, uri, out) =>
      val a = out.resolve("a.txt")
      IO.write(a.toFile, "from a file")
      val value = StringVirtualFile1(out.resolve("value.json").toString, "42")
      val outputs = Vector[VirtualFile](value, converter.toVirtualFile(a))
      Using.resource(newStore(uri)): store =>
        val stored = store.put(UpdateActionResultRequest(actionDigest, outputs, exitCode = 0))
        assert(stored.isRight)
        assert(remote.ac.size == 1)
        assert(remote.cas.size == 2)
      Files.delete(a)
      Using.resource(newStore(uri)): store =>
        val result = store.get(GetActionResultRequest(actionDigest)) match
          case Right(r) => r
          case Left(e)  => throw e
        assert(result.origin == Some("remote"))
        assert(result.exitCode == Some(0))
        assert(result.outputFiles.map(_.id) == outputs.map(_.id))
        assert(store.findBlobs(result.outputFiles).size == 2)
        val paths = store.syncBlobs(result.outputFiles, out)
        assert(paths.size == 2)
        assert(IO.read(a.toFile) == "from a file")
        assert(IO.read(out.resolve("value.json").toFile) == "42")

  test("an empty blob and duplicated content round trip"):
    withRemote(): (_, uri, out) =>
      val outputs = Vector[VirtualFile](
        StringVirtualFile1(out.resolve("empty.txt").toString, ""),
        StringVirtualFile1(out.resolve("x.txt").toString, "same"),
        StringVirtualFile1(out.resolve("y.txt").toString, "same"),
      )
      Using.resource(newStore(uri)): store =>
        assert(store.put(UpdateActionResultRequest(actionDigest, outputs, 0)).isRight)
      Using.resource(newStore(uri)): store =>
        val result = store.get(GetActionResultRequest(actionDigest)).toOption.get
        assert(store.findBlobs(result.outputFiles).size == 3)
        assert(store.syncBlobs(result.outputFiles, out).size == 3)
        assert(IO.read(out.resolve("empty.txt").toFile) == "")
        assert(IO.read(out.resolve("y.txt").toFile) == "same")

  List("SHA-1" -> "sha1", "SHA-384" -> "sha384", "SHA-512" -> "sha512").foreach: (jvmAlgo, algo) =>
    test(s"round trip with $algo digests"):
      withRemote(jvmAlgo): (remote, uri, out) =>
        val action = digestOf(jvmAlgo, algo, "action")
        val blob = hashedFile(jvmAlgo, algo, out.resolve("blob.txt"), "content")
        Using.resource(newStore(uri)): store =>
          assert(store.put(UpdateActionResultRequest(action, Vector(blob), 0)).isRight)
          assert(remote.ac.containsKey(action.hashHexString))
        Using.resource(newStore(uri)): store =>
          val result = store.get(GetActionResultRequest(action)).toOption.get
          assert(result.outputFiles.map(_.contentHashStr) == Vector(blob.contentHashStr))
          assert(store.findBlobs(result.outputFiles).size == 1)
          assert(store.syncBlobs(result.outputFiles, out).size == 1)
          assert(IO.read(out.resolve("blob.txt").toFile) == "content")

  test("digests that the server does not accept are treated as not cached"):
    withRemote(): (remote, uri, out) =>
      val action = digestOf("SHA-512", "sha512", "action")
      val blob = hashedFile("SHA-512", "sha512", out.resolve("blob.txt"), "content")
      Using.resource(newStore(uri)): store =>
        assert(store.putBlobs(Seq(blob)).isEmpty)
        assert(store.findBlobs(Seq[HashedVirtualFileRef](blob)).isEmpty)
        assert(store.put(UpdateActionResultRequest(action, Vector(blob), 0)).isLeft)
        assert(store.get(GetActionResultRequest(action)).isLeft)
        assert(remote.ac.isEmpty)
        assert(remote.cas.isEmpty)

  test("blobs with a non-cryptographic digest are skipped without a request"):
    withRemote(): (remote, uri, out) =>
      val blob = new StringVirtualFile1(out.resolve("blob.txt").toString, "content"):
        override def contentHashStr: String = "xx64-" + ("0" * 16)
      Using.resource(newStore(uri)): store =>
        assert(store.putBlobs(Seq(blob)).isEmpty)
        assert(store.findBlobs(Seq[HashedVirtualFileRef](blob)).isEmpty)
      assert(remote.recorded.isEmpty)

  test("get of an unknown action is a miss"):
    withRemote(): (_, uri, _) =>
      Using.resource(newStore(uri)): store =>
        assert(store.get(GetActionResultRequest(actionDigest)).isLeft)

  test("get is a miss when a referenced blob is gone from the remote"):
    withRemote(): (remote, uri, out) =>
      val value = StringVirtualFile1(out.resolve("value.json").toString, "42")
      Using.resource(newStore(uri)): store =>
        assert(store.put(UpdateActionResultRequest(actionDigest, Vector(value), 0)).isRight)
        remote.cas.clear()
        assert(store.get(GetActionResultRequest(actionDigest)).isLeft)

  test("uploads carry Content-Length, which bazel-remote requires"):
    withRemote(): (remote, uri, out) =>
      val a = out.resolve("a.txt")
      IO.write(a.toFile, "from a file")
      val outputs = Vector[VirtualFile](
        StringVirtualFile1(out.resolve("value.json").toString, "42"),
        converter.toVirtualFile(a),
      )
      Using.resource(newStore(uri)): store =>
        assert(store.put(UpdateActionResultRequest(actionDigest, outputs, 0)).isRight)
      val puts = remote.recorded.filter(_.method == "PUT")
      assert(puts.size == 3)
      assert(puts.forall(_.headers.contains("content-length")))
      assert(puts.forall(!_.headers.contains("transfer-encoding")))

  test("findBlobs reports only the blobs present on the remote"):
    withRemote(): (_, uri, out) =>
      val present = StringVirtualFile1(out.resolve("present.txt").toString, "present")
      val absent = StringVirtualFile1(out.resolve("absent.txt").toString, "absent")
      Using.resource(newStore(uri)): store =>
        assert(store.putBlobs(Seq(present)).size == 1)
        val found = store.findBlobs(Seq[HashedVirtualFileRef](present, absent))
        assert(found.map(_.id) == Seq(present.id))

  test("a rejected blob upload is not reported, and no action result is written"):
    withRemote(): (remote, uri, out) =>
      val good = StringVirtualFile1(out.resolve("good.txt").toString, "good")
      val bad = new StringVirtualFile1(out.resolve("bad.txt").toString, "bad"):
        override def contentHashStr: String = "sha256-" + ("0" * 64)
      Using.resource(newStore(uri)): store =>
        assert(store.putBlobs(Seq(good, bad)).map(_.id) == Seq(good.id))
        val stored = store.put(UpdateActionResultRequest(actionDigest, Vector(good, bad), 0))
        assert(stored.isLeft)
        assert(remote.ac.isEmpty)

  test("syncBlobs skips a blob that is missing from the remote"):
    withRemote(): (_, uri, out) =>
      val absent = StringVirtualFile1(out.resolve("absent.txt").toString, "absent")
      Using.resource(newStore(uri)): store =>
        assert(store.syncBlobs(Seq[HashedVirtualFileRef](absent), out).isEmpty)
        assert(!Files.exists(out.resolve("absent.txt")))

  test("syncBlobs works after the disk cache directory is deleted"):
    withRemote(): (_, uri, out) =>
      val blob = StringVirtualFile1(out.resolve("blob.txt").toString, "content")
      val base = Files.createTempDirectory("http-action-cache-test")
      val disk = DiskActionCacheStore(base, converter)
      Using.resource(HttpActionCacheStore(uri, Nil, disk, 30.seconds)): store =>
        assert(store.putBlobs(Seq(blob)).size == 1)
        assert(Files.isDirectory(disk.casBase))
        IO.delete(base.toFile)
        assert(store.syncBlobs(Seq[HashedVirtualFileRef](blob), out).size == 1)
        assert(IO.read(out.resolve("blob.txt").toFile) == "content")

  test("syncBlobs refuses a blob whose content does not match its digest"):
    withRemote(): (remote, uri, out) =>
      val blob = StringVirtualFile1(out.resolve("blob.txt").toString, "expected")
      val digest = Digest(blob: HashedVirtualFileRef)
      remote.cas.put(digest.hashHexString, "tampered".getBytes(StandardCharsets.UTF_8))
      Using.resource(newStore(uri)): store =>
        intercept[IOException]:
          store.syncBlobs(Seq[HashedVirtualFileRef](blob), out)
        assert(!Files.exists(out.resolve("blob.txt")))

  test("headers and the instance name are sent with every request"):
    withRemote(): (remote, uri, out) =>
      val value = StringVirtualFile1(out.resolve("value.json").toString, "42")
      val instanceUri = URI(s"$uri/my/instance/")
      Using.resource(newStore(instanceUri, List("x-api-key=ab=cd"))): store =>
        assert(store.put(UpdateActionResultRequest(actionDigest, Vector(value), 0)).isRight)
        assert(store.get(GetActionResultRequest(actionDigest)).isRight)
      val recorded = remote.recorded
      assert(recorded.nonEmpty)
      assert(recorded.forall(_.headers.get("x-api-key") == Some("ab=cd")))
      assert(recorded.forall(_.path.startsWith("/my/instance/")))
      assert(recorded.exists(_.path == s"/my/instance/ac/${actionDigest.hashHexString}"))

  test("credentials in the URI are sent as Basic authentication"):
    withRemote(): (remote, uri, _) =>
      val withUser = URI(s"http://user:pw@127.0.0.1:${uri.getPort}")
      Using.resource(newStore(withUser)): store =>
        assert(store.get(GetActionResultRequest(actionDigest)).isLeft)
      assert(
        remote.recorded.map(_.headers.get("authorization")) == List(Some("Basic dXNlcjpwdw=="))
      )

  test("an explicit authorization header wins over credentials in the URI"):
    val uri = URI("https://user:pw@example.com")
    val headers = HttpActionCacheStore.requestHeaders(uri, List("authorization=Bearer token"))
    assert(headers == List("authorization" -> "Bearer token"))

  test("baseUrl drops credentials and the trailing slash"):
    assert(
      HttpActionCacheStore.baseUrl(URI("https://user:pw@example.com:8443/inst/")) ==
        "https://example.com:8443/inst"
    )
    assert(HttpActionCacheStore.baseUrl(URI("http://example.com")) == "http://example.com")
    intercept[RuntimeException]:
      HttpActionCacheStore.baseUrl(URI("grpc://example.com"))

  test("redact removes credentials from the URI"):
    assert(
      HttpActionCacheStore.redact(URI("http://user:pw@example.com:8000/inst")) ==
        URI("http://example.com:8000/inst")
    )

  private def digestOf(jvmAlgo: String, algo: String, content: String): Digest =
    val bytes = content.getBytes(StandardCharsets.UTF_8)
    Digest(algo, MessageDigest.getInstance(jvmAlgo).digest(bytes), bytes.length.toLong)

  private def hashedFile(jvmAlgo: String, algo: String, path: Path, content: String): VirtualFile =
    val digest = digestOf(jvmAlgo, algo, content)
    new StringVirtualFile1(path.toString, content):
      override def contentHashStr: String = s"$algo-${digest.hashHexString}"

  private def withRemote(
      algorithm: String = "SHA-256"
  )(f: (FakeBazelRemote, URI, Path) => Unit): Unit =
    Using.resource(FakeBazelRemote(algorithm)): remote =>
      IO.withTemporaryDirectory: dir =>
        f(remote, remote.uri, dir.toPath.toRealPath())

  private def newStore(uri: URI, headers: List[String] = Nil): HttpActionCacheStore =
    val base = Files.createTempDirectory("http-action-cache-test")
    val disk = DiskActionCacheStore(base, converter)
    HttpActionCacheStore(uri, headers, disk, 30.seconds)
end HttpActionCacheStoreTest
