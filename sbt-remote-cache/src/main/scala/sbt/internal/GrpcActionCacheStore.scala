package sbt
package internal

import build.bazel.remote.execution.v2.{
  ActionCacheGrpc,
  ActionResult as XActionResult,
  BatchReadBlobsRequest,
  BatchReadBlobsResponse,
  BatchUpdateBlobsRequest,
  BatchUpdateBlobsResponse,
  Compressor,
  ContentAddressableStorageGrpc,
  Digest as XDigest,
  DigestFunction,
  FindMissingBlobsRequest,
  GetActionResultRequest as XGetActionResultRequest,
  OutputFile,
  UpdateActionResultRequest as XUpdateActionResultRequest,
}
import com.eed3si9n.remoteapis.shaded.com.google.protobuf.ByteString
import com.eed3si9n.remoteapis.shaded.io.grpc.{
  CallCredentials,
  Grpc,
  ManagedChannel,
  ManagedChannelBuilder,
  Metadata,
  Status,
  TlsChannelCredentials,
}
import com.eed3si9n.remoteapis.shaded.io.grpc.stub.StreamObserver
import com.eed3si9n.remoteapis.shaded.com.google.bytestream.ByteStreamGrpc
import com.eed3si9n.remoteapis.shaded.com.google.bytestream.ByteStreamProto
import ByteStreamProto.{ ReadRequest, WriteRequest }
import java.io.InputStream
import java.net.URI
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.{ Executors, TimeUnit }
import sbt.io.syntax.*
import sbt.util.{
  AbstractActionCacheStore,
  ActionResult,
  Digest,
  DiskActionCacheStore,
  GetActionResultRequest,
  UpdateActionResultRequest,
}
import scala.concurrent.{ Await, ExecutionContext, Future, Promise, TimeoutException }
import scala.concurrent.duration.*
import scala.util.Using
import scala.util.control.NonFatal
import scala.jdk.CollectionConverters.*
import xsbti.{ HashedVirtualFileRef, VirtualFile }

object GrpcActionCacheStore:
  // chunk uploads to 1MB
  val chunkSizeBytes = 1024 * 1024
  val remoteTimeoutInSec = 60
  val remoteTimeout = (remoteTimeoutInSec + 2).second

  def apply(
      uri: URI,
      rootCerts: Option[Path],
      clientCertChain: Option[Path],
      clientPrivateKey: Option[Path],
      remoteHeaders: List[String],
      disk: DiskActionCacheStore,
  ): GrpcActionCacheStore =
    val b: ManagedChannelBuilder[?] = uri.getScheme() match
      case "grpc" =>
        val port = uri.getPort() match
          case p if p >= 0 => p
          case _           => 80
        val builder = ManagedChannelBuilder.forAddress(uri.getHost(), port)
        builder.usePlaintext()
        builder
      case "grpcs" =>
        val port = uri.getPort() match
          case p if p >= 0 => p
          case _           => 443
        // https://grpc.github.io/grpc-java/javadoc/io/grpc/TlsChannelCredentials.Builder.html
        val tlsBuilder = TlsChannelCredentials.newBuilder()
        rootCerts.foreach: cert =>
          tlsBuilder.trustManager(cert.toFile())
        (clientCertChain, clientPrivateKey) match
          case (Some(cert), Some(key)) =>
            tlsBuilder.keyManager(cert.toFile(), key.toFile())
          case _ => ()
        Grpc.newChannelBuilderForAddress(
          uri.getHost(),
          port,
          tlsBuilder.build(),
        )
      case scheme => sys.error(s"unsupported $uri")
    val channel = b.build()
    val instanceName = Option(uri.getPath()) match
      case Some(x) if x.startsWith("/") => x.drop(1)
      case Some(x)                      => x
      case None                         => ""
    new GrpcActionCacheStore(channel, instanceName, remoteHeaders, disk)

  class AuthCallCredentials(remoteHeaders: List[String]) extends CallCredentials:
    val pairs = remoteHeaders.map: h =>
      h.split("=").toList match
        case List(k, v) => Metadata.Key.of(k, Metadata.ASCII_STRING_MARSHALLER) -> v
        case _          => sys.error("remote header must contain one '='")
    override def applyRequestMetadata(
        requestInfo: CallCredentials.RequestInfo,
        executor: java.util.concurrent.Executor,
        applier: CallCredentials.MetadataApplier
    ): Unit =
      executor.execute: () =>
        try
          val headers = Metadata()
          pairs.foreach { (k, v) =>
            headers.put(k, v)
          }
          applier.apply(headers)
        catch case NonFatal(e) => applier.fail(Status.UNAUTHENTICATED.withCause(e))
  end AuthCallCredentials

  def chunkBytes(sizeBytes: Long): List[Long] =
    if sizeBytes <= 0 then Nil
    else
      val full = sizeBytes / chunkSizeBytes
      val remainder = sizeBytes % chunkSizeBytes
      val xs = List.fill(full.toInt)(chunkSizeBytes.toLong)
      if remainder > 0 then xs ::: List(remainder)
      else xs
end GrpcActionCacheStore

/*
 * https://github.com/bazelbuild/remote-apis/blob/main/build/bazel/remote/execution/v2/remote_execution.proto
 * https://github.com/googleapis/googleapis/blob/ff15be54722218705740b9fc6223d264c4cdb6dd/google/bytestream/bytestream.proto
 */
class GrpcActionCacheStore(
    channel: ManagedChannel,
    instanceName: String,
    remoteHeaders: List[String],
    disk: DiskActionCacheStore,
) extends AbstractActionCacheStore:
  import GrpcActionCacheStore.*

  lazy val creds = GrpcActionCacheStore.AuthCallCredentials(remoteHeaders)
  lazy val acStub0 = ActionCacheGrpc.newBlockingStub(channel)
  lazy val acStub = remoteHeaders match
    case x :: xs => acStub0.withCallCredentials(creds)
    case _       => acStub0
  lazy val casStub0 = ContentAddressableStorageGrpc.newBlockingStub(channel)
  lazy val casStub = remoteHeaders match
    case x :: xs => casStub0.withCallCredentials(creds)
    case _       => casStub0
  lazy val byteStreamStub0 = ByteStreamGrpc.newStub(channel)
  lazy val byteStreamStub = remoteHeaders match
    case x :: xs =>
      byteStreamStub0
        .withCallCredentials(creds)
        .withDeadlineAfter(remoteTimeoutInSec, TimeUnit.SECONDS)
    case _ =>
      byteStreamStub0.withDeadlineAfter(remoteTimeoutInSec, TimeUnit.SECONDS)

  override def storeName: String = "remote"

  val fixedThreadPool = Executors.newFixedThreadPool(100)
  given ExecutionContext = ExecutionContext.fromExecutor(fixedThreadPool)

  /**
   * https://github.com/bazelbuild/remote-apis/blob/9ff14cecffe5287ba337f857731ceadfc2d80de9/build/bazel/remote/execution/v2/remote_execution.proto#L170
   */
  override def get(request: GetActionResultRequest): Either[Throwable, ActionResult] =
    try
      val b = XGetActionResultRequest.newBuilder()
      b.setInstanceName(instanceName)
      b.setActionDigest(toXDigest(request.actionDigest))
      b.setDigestFunction(DigestFunction.Value.SHA256)
      request.inlineOutputFiles.foreach: p =>
        b.addInlineOutputFiles(p)
      val req = b.build()
      val result = acStub.getActionResult(req)
      Right(toActionResult(result))
    catch case NonFatal(e) => Left(e)

  /**
   * https://github.com/bazelbuild/remote-apis/blob/9ff14cecffe5287ba337f857731ceadfc2d80de9/build/bazel/remote/execution/v2/remote_execution.proto#L1596
   */
  override def put(request: UpdateActionResultRequest): Either[Throwable, ActionResult] =
    try
      val refs = putBlobsIfNeeded(request.outputFiles)
      val b = XUpdateActionResultRequest.newBuilder()
      b.setInstanceName(instanceName)
      b.setActionDigest(toXDigest(request.actionDigest))
      b.setDigestFunction(DigestFunction.Value.SHA256)
      b.setActionResult(toXActionResult(refs, request.exitCode))
      val req = b.build()
      val result = acStub.updateActionResult(req)
      Right(toActionResult(result))
    catch case NonFatal(e) => Left(e)

  /**
   * https://github.com/bazelbuild/remote-apis/blob/9ff14cecffe5287ba337f857731ceadfc2d80de9/build/bazel/remote/execution/v2/remote_execution.proto#L403
   */
  override def syncBlobs(refs: Seq[HashedVirtualFileRef], outputDirectory: Path): Seq[Path] =
    val digests = refs.map(Digest(_))
    val totalBytes = digests.map(_.sizeBytes).sum
    if refs.isEmpty then Nil
    else if totalBytes <= chunkSizeBytes then
      val result = batchReadBlobs(refs)
      val blobs = result.getResponsesList().asScala.toList
      val allOk = blobs.forall(_.getStatus().getCode() == 0)
      if allOk then
        // do not assume the responses to come in order
        val lookupResponse: Map[Digest, BatchReadBlobsResponse.Response] =
          Map(blobs.map(res => toDigest(res.getDigest) -> res)*)
        refs.map: r =>
          val digest = Digest(r)
          val blob = lookupResponse(digest)
          val casFile = disk.putBlob(blob.getData().newInput(), digest)
          disk.syncFile(r, casFile, outputDirectory)
      else Nil
    else
      val paths = Await.result(downloadBlobs(digests, outputDirectory), remoteTimeout)
      refs
        .zip(digests)
        .zip(paths)
        .map { case ((r, digest), p) =>
          val casFile = disk.putBlobInternal(p, digest)
          disk.syncFile(r, casFile, outputDirectory)
        }

  /**
   * https://github.com/bazelbuild/remote-apis/blob/9ff14cecffe5287ba337f857731ceadfc2d80de9/build/bazel/remote/execution/v2/remote_execution.proto#L379
   */
  override def putBlobs(blobs: Seq[VirtualFile]): Seq[HashedVirtualFileRef] =
    val totalBytes = blobs.map(_.sizeBytes).sum
    if blobs.isEmpty then Nil
    else if totalBytes <= chunkSizeBytes then batchUpdateBlobs(blobs)
    else
      try Await.result(uploadBlobs(blobs).recover(_ => Nil), remoteTimeout)
      catch case _: TimeoutException => Nil

  def batchUpdateBlobs(blobs: Seq[VirtualFile]): Seq[HashedVirtualFileRef] =
    val b = BatchUpdateBlobsRequest.newBuilder()
    b.setInstanceName(instanceName)
    b.setDigestFunction(DigestFunction.Value.SHA256)
    blobs.foreach: blob =>
      val bb = BatchUpdateBlobsRequest.Request.newBuilder()
      bb.setDigest(toXDigest(Digest(blob)))
      bb.setData(toByteString(blob))
      bb.setCompressor(Compressor.Value.IDENTITY)
      b.addRequests(bb.build())
    val req = b.build()
    val result = casStub.batchUpdateBlobs(req)
    val responses = result.getResponsesList().asScala.toList
    // do not assume responses to come in order
    val lookupResponse: Map[Digest, BatchUpdateBlobsResponse.Response] =
      Map(responses.map(res => toDigest(res.getDigest()) -> res)*)
    blobs.flatMap: blob =>
      val d = Digest(blob)
      if lookupResponse.contains(d) then
        Some(HashedVirtualFileRef.of(blob.id(), d.contentHashStr, d.sizeBytes))
      else None

  def uploadBlobs(blobs: Seq[VirtualFile]): Future[Seq[HashedVirtualFileRef]] =
    Future.sequence(blobs.map(uploadBlob))

  def uploadBlob(blob: VirtualFile): Future[HashedVirtualFileRef] =
    val d = Digest(blob)
    withSingleResponse[ByteStreamProto.WriteResponse, HashedVirtualFileRef]: (p, resObs) =>
      val reqObs = byteStreamStub.write(resObs)
      val un = uploadName(d, UUID.randomUUID())
      var off: Long = 0L
      try
        Using.resource(blob.input()): input =>
          val chunks = chunkBytes(d.sizeBytes)
          chunks.zipWithIndex.foreach: (chunk, idx) =>
            val b = WriteRequest.newBuilder()
            if idx == 0 then b.setResourceName(un)
            else ()
            b.setWriteOffset(off)
            b.setData(toByteString(input, chunk))
            if idx == chunks.size - 1 then b.setFinishWrite(true)
            else ()
            val req = b.build()
            off = off + chunk
            reqObs.onNext(req)
      catch
        case NonFatal(e) =>
          reqObs.onError(e)
          p.failure(e)
      reqObs.onCompleted()
      p.future.map: _ =>
        HashedVirtualFileRef.of(blob.id(), d.contentHashStr, d.sizeBytes)

  private def downloadBlobs(digests: Seq[Digest], outputDirectory: Path): Future[Seq[Path]] =
    Future.sequence(digests.map: x =>
      downloadBlob(x, outputDirectory))

  private def downloadBlob(digest: Digest, outputDirectory: Path): Future[Path] =
    val p = Promise[Path]()
    val uuid = UUID.randomUUID()
    val tempFile = outputDirectory.toFile() / s"$uuid.part"
    sbt.io.Using.fileOutputStream(false)(tempFile): out =>
      val resObs = new StreamObserver[ByteStreamProto.ReadResponse]:
        override def onCompleted(): Unit =
          p.success(tempFile.toPath())
        override def onError(e: Throwable): Unit = p.failure(e)
        override def onNext(res: ByteStreamProto.ReadResponse): Unit =
          IO.transfer(res.getData().newInput(), out)
      val b = ReadRequest.newBuilder()
      val dn = downloadName(digest)
      b.setResourceName(dn)
      b.setReadOffset(0L)
      val req = b.build()
      byteStreamStub.read(req, resObs)
      p.future

  // helper function for many-to-one gRPC streaming
  // https://grpc.io/docs/languages/java/basics/#client-side-streaming-rpc-1
  private def withSingleResponse[A1, A2](
      f: (Promise[A1], StreamObserver[A1]) => Future[A2]
  ): Future[A2] =
    val p = Promise[A1]()
    val observer = new StreamObserver[A1]:
      var o: Option[A1] = None
      override def onCompleted(): Unit =
        if o.isDefined then p.success(o.get)
        else p.failure(new RuntimeException("unexpected onCompleted"))
      override def onError(e: Throwable): Unit = p.failure(e)
      override def onNext(res: A1): Unit =
        o = Some(res)
    f(p, observer)

  /**
   * resource name is load-bearing.
   * https://github.com/bazelbuild/remote-apis/blob/main/build/bazel/remote/execution/v2/remote_execution.proto#L219-L220
   */
  private def uploadName(d: Digest, uuid: UUID): String =
    s"$instanceName/uploads/$uuid/blobs/${d.algo}/${d.hashHexString}/${d.sizeBytes}"

  /**
   * resource name is load-bearing.
   * https://github.com/bazelbuild/remote-apis/blob/main/build/bazel/remote/execution/v2/remote_execution.proto#L294-L295
   */
  private def downloadName(d: Digest): String =
    s"$instanceName/blobs/${d.algo}/${d.hashHexString}/${d.sizeBytes}"

  /**
   * https://github.com/bazelbuild/remote-apis/blob/96942a2107c702ed3ca4a664f7eeb7c85ba8dc77/build/bazel/remote/execution/v2/remote_execution.proto#L1629
   */
  override def findBlobs(refs: Seq[HashedVirtualFileRef]): Seq[HashedVirtualFileRef] =
    val b = FindMissingBlobsRequest.newBuilder()
    b.setInstanceName(instanceName)
    refs.foreach: r =>
      b.addBlobDigests(toXDigest(Digest(r)))
    b.setDigestFunction(DigestFunction.Value.SHA256)
    val req = b.build()
    val res = casStub.findMissingBlobs(req)
    val missing = res.getMissingBlobDigestsList().asScala.map(toDigest).toSet
    refs.flatMap: r =>
      if missing(Digest(r)) then None
      else Some(r)

  private def batchReadBlobs(refs: Seq[HashedVirtualFileRef]): BatchReadBlobsResponse =
    val b = BatchReadBlobsRequest.newBuilder()
    b.setInstanceName(instanceName)
    refs.foreach: ref =>
      b.addDigests(toXDigest(Digest(ref)))
    b.setDigestFunction(DigestFunction.Value.SHA256)
    b.addAcceptableCompressors(Compressor.Value.IDENTITY)
    val req = b.build()
    casStub.batchReadBlobs(req)

  private def toXActionResult(
      refs: Seq[HashedVirtualFileRef],
      exitCode: Option[Int]
  ): XActionResult =
    val b = XActionResult.newBuilder()
    exitCode.foreach: e =>
      b.setExitCode(e)
    refs.foreach: ref =>
      val out = toOutputFile(ref)
      b.addOutputFiles(out)
    b.build()

  // per spec, Clients SHOULD NOT populate [contents] when uploading to the cache.
  private def toOutputFile(ref: HashedVirtualFileRef): OutputFile =
    val b = OutputFile.newBuilder()
    b.setPath(ref.id)
    b.setDigest(toXDigest(Digest(ref)))
    b.build()

  def toActionResult(ar: XActionResult): ActionResult =
    val outs = ar.getOutputFilesList.asScala.toVector.map: out =>
      val d = toDigest(out.getDigest())
      HashedVirtualFileRef.of(out.getPath(), d.contentHashStr, d.sizeBytes)
    ActionResult(outs, storeName, ar.getExitCode())

  def toXDigest(d: Digest): XDigest =
    val str = d.contentHashStr.split("-")(1)
    val sizeBytes = d.sizeBytes
    val b = XDigest.newBuilder()
    b.setHash(str)
    b.setSizeBytes(sizeBytes)
    b.build()

  def toDigest(d: XDigest): Digest =
    val hash = d.getHash()
    val sizeBytes = d.getSizeBytes()
    Digest(s"sha256-$hash/$sizeBytes")

  private def toByteString(blob: VirtualFile): ByteString =
    val out = ByteString.newOutput()
    IO.transfer(blob.input(), out)
    out.toByteString()

  private def toByteString(input: InputStream, size: Long): ByteString =
    val BufferSize = 8192
    val out = ByteString.newOutput()
    if size <= 0 then out.toByteString()
    else
      var buf = new Array[Byte](BufferSize)
      var remaining = size
      while
        if remaining >= BufferSize then
          if buf.size != BufferSize then buf = new Array[Byte](BufferSize)
          else ()
        else buf = new Array[Byte](remaining.toInt)
        val readBytes = input.read(buf)
        if readBytes > 0 then out.write(buf, 0, readBytes)
        else ()
        remaining = remaining - readBytes
        readBytes > 0
      do ()
      out.toByteString()
end GrpcActionCacheStore
