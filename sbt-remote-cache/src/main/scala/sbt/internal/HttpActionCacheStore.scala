package sbt
package internal

import build.bazel.remote.execution.v2.ActionResult as XActionResult
import gigahorse.{ Config, HttpVersionPolicy, InMemoryBody, Request, StatusError }
import gigahorse.support.apachehttp.{ ApacheByteStreamHandler, ApacheHttpClient, Gigahorse }
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path, StandardOpenOption }
import java.util.{ Base64, UUID }
import sbt.io.IO
import sbt.util.{
  AbstractActionCacheStore,
  ActionResult,
  Digest,
  DiskActionCacheStore,
  GetActionResultRequest,
  UpdateActionResultRequest,
}
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration.*
import scala.util.Using
import scala.util.control.NonFatal
import xsbti.{ HashedVirtualFileRef, PathBasedFile, VirtualFile }

object HttpActionCacheStore:
  val maxConnections = 64

  def apply(
      uri: URI,
      remoteHeaders: List[String],
      disk: DiskActionCacheStore,
      requestTimeout: FiniteDuration,
  ): HttpActionCacheStore =
    val config = Config()
      .withHttpVersionPolicy(HttpVersionPolicy.Http1_1)
      .withMaxConnections(maxConnections)
      .withMaxConnectionsPerHost(maxConnections)
      .withRequestTimeout(requestTimeout)
      .withReadTimeout(requestTimeout)
    new HttpActionCacheStore(
      ApacheHttpClient(config),
      baseUrl(uri),
      requestHeaders(uri, remoteHeaders),
      disk,
      requestTimeout,
    )

  /** The endpoint without credentials or a trailing slash; the path is the instance name. */
  private[internal] def baseUrl(uri: URI): String =
    val path = Option(uri.getPath()).getOrElse("").reverse.dropWhile(_ == '/').reverse
    val scheme = uri.getScheme() match
      case s @ ("http" | "https") => s
      case _                      => sys.error(s"unsupported ${redact(uri)}")
    URI(scheme, null, uri.getHost(), uri.getPort(), path, null, null).toString

  /** Removes the userinfo so that the URI is safe to log. */
  private[sbt] def redact(uri: URI): URI =
    if uri.getRawUserInfo() == null then uri
    else
      URI(
        uri.getScheme(),
        null,
        uri.getHost(),
        uri.getPort(),
        uri.getPath(),
        uri.getQuery(),
        uri.getFragment()
      )

  /** Explicit headers win over the credentials embedded in the URI. */
  private[internal] def requestHeaders(
      uri: URI,
      remoteHeaders: List[String]
  ): List[(String, String)] =
    val explicit = BazelRemote.parseHeaders(remoteHeaders)
    val hasAuthorization = explicit.exists(_._1.equalsIgnoreCase("authorization"))
    Option(uri.getUserInfo()) match
      case Some(userInfo) if !hasAuthorization =>
        val encoded =
          Base64.getEncoder().encodeToString(userInfo.getBytes(StandardCharsets.UTF_8))
        ("Authorization" -> s"Basic $encoded") :: explicit
      case _ => explicit

  private class DownloadHandler(target: Path)
      extends ApacheByteStreamHandler[Option[Path]]
      with AutoCloseable:
    private var status: Int = 0
    private var channel: Option[FileChannel] = None

    override def onStatusReceived(code: Int): Unit =
      status = code
      if code == 200 then
        channel = Some(
          FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        )

    override def onByteReceived(buf: ByteBuffer): Unit =
      channel.foreach: ch =>
        while buf.hasRemaining() do ch.write(buf)

    override def onCompleted(): Option[Path] =
      close()
      status match
        case 200 => Some(target)
        case 404 => None
        case _   => throw StatusError(status)

    override def close(): Unit =
      channel.foreach(_.close())
      channel = None
  end DownloadHandler
end HttpActionCacheStore

/**
 * A cache store that speaks the HTTP/1.1 REST API of bazel-remote:
 * `/ac/<hash>` holds ActionResult protobuf messages, and `/cas/<hash>` holds blobs.
 * Hashes may be SHA-1, SHA-256, SHA-384, or SHA-512; a server that does not accept
 * the algorithm in use rejects the request, which is treated as not cached.
 *
 * https://github.com/buchgr/bazel-remote#http11-rest-api
 */
class HttpActionCacheStore private (
    http: ApacheHttpClient,
    baseUrl: String,
    headers: List[(String, String)],
    disk: DiskActionCacheStore,
    requestTimeout: FiniteDuration,
) extends AbstractActionCacheStore
    with AutoCloseable:
  import HttpActionCacheStore.DownloadHandler

  private val awaitTimeout = requestTimeout + 2.seconds
  private given ExecutionContext = ExecutionContext.parasitic

  override def storeName: String = "remote"

  override def close(): Unit = http.close()

  override def get(request: GetActionResultRequest): Either[Throwable, ActionResult] =
    try
      val res = await(http.processFull(acRequest(request.actionDigest)))
      res.status match
        case 200 =>
          val xar = XActionResult.parseFrom(res.bodyAsByteBuffer)
          Right(BazelRemote.toActionResult(xar, storeName))
        case 404    => Left(notFound)
        case status => Left(StatusError(status))
    catch case NonFatal(e) => Left(e)

  override def put(request: UpdateActionResultRequest): Either[Throwable, ActionResult] =
    try
      val refs = putBlobsIfNeeded(request.outputFiles)
      if refs.size != request.outputFiles.size then
        Left(RuntimeException(s"failed to upload ${request.outputFiles.size - refs.size} blobs"))
      else
        val xar = BazelRemote.toXActionResult(refs, request.exitCode)
        val req = acRequest(request.actionDigest)
          .withMethod("PUT")
          .withBody(InMemoryBody(xar.toByteArray()))
        await(statusOf(req)) match
          case status if isOk(status) =>
            Right(ActionResult(refs.toVector, Some(storeName), request.exitCode))
          case status => Left(StatusError(status))
    catch case NonFatal(e) => Left(e)

  override def putBlobs(blobs: Seq[VirtualFile]): Seq[HashedVirtualFileRef] =
    val uploads = blobs
      .filter(BazelRemote.isSupported)
      .map(b => Digest(b) -> b)
      .distinctBy(_._1)
      .map: (digest, blob) =>
        statusOf(uploadRequest(digest, blob))
          .map(status => digest -> isOk(status))
          .recover { case NonFatal(_) => digest -> false }
    val uploaded = await(Future.sequence(uploads)).collect { case (digest, true) => digest }.toSet
    blobs.flatMap: blob =>
      if BazelRemote.isSupported(blob) && uploaded(Digest(blob)) then
        Some(HashedVirtualFileRef.of(blob.id, blob.contentHashStr, blob.sizeBytes))
      else None

  override def findBlobs(refs: Seq[HashedVirtualFileRef]): Seq[HashedVirtualFileRef] =
    val lookups = refs
      .filter(BazelRemote.isSupported)
      .map(Digest(_))
      .distinct
      .map: digest =>
        statusOf(casRequest(digest).head).map(status => digest -> (status == 200))
    val found = await(Future.sequence(lookups)).collect { case (digest, true) => digest }.toSet
    refs.filter(r => BazelRemote.isSupported(r) && found(Digest(r)))

  /**
   * A blob missing from the remote is skipped, whereas any other failure is thrown
   * so that the caller falls back to running the task.
   */
  override def syncBlobs(refs: Seq[HashedVirtualFileRef], outputDirectory: Path): Seq[Path] =
    val digests = refs.filter(BazelRemote.isSupported).map(Digest(_)).distinct
    if digests.nonEmpty then Files.createDirectories(disk.casBase)
    val downloads = digests.map: digest =>
      downloadBlob(digest).map(_.map(digest -> _))
    val casFiles = await(Future.sequence(downloads)).flatten.toMap
    refs.flatMap: r =>
      if BazelRemote.isSupported(r) then
        casFiles.get(Digest(r)).map(disk.syncFile(r, _, outputDirectory))
      else None

  private def downloadBlob(digest: Digest): Future[Option[Path]] =
    val tempFile = disk.casBase.resolve(s"${UUID.randomUUID()}.part")
    val handler = DownloadHandler(tempFile)
    http
      .processByteStream(casRequest(digest), handler)
      .recover { case e: StatusError if e.status == 404 => None }
      .map(_.map(disk.putBlobInternal(_, digest)))
      .andThen: _ =>
        handler.close()
        Files.deleteIfExists(tempFile)

  private def uploadRequest(digest: Digest, blob: VirtualFile): Request =
    blob match
      case p: PathBasedFile => casRequest(digest).put(p.toPath().toFile())
      case _                =>
        val bytes = Using.resource(blob.input())(IO.readBytes)
        casRequest(digest).withMethod("PUT").withBody(InMemoryBody(bytes))

  private def acRequest(digest: Digest): Request =
    if !BazelRemote.isSupported(digest.algo) then sys.error(s"unsupported action digest: $digest")
    else Gigahorse.url(s"$baseUrl/ac/${digest.hashHexString}").addHeaders(headers*)

  private def casRequest(digest: Digest): Request =
    Gigahorse.url(s"$baseUrl/cas/${digest.hashHexString}").addHeaders(headers*)

  private def statusOf(req: Request): Future[Int] =
    http
      .processFull(req)
      .map(_.status)
      .recover { case e: StatusError => e.status }

  private def isOk(status: Int): Boolean = status >= 200 && status < 300

  private def await[A](f: Future[A]): A = Await.result(f, awaitTimeout)
end HttpActionCacheStore
