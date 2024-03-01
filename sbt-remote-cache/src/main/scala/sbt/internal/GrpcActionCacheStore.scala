package sbt
package internal

import build.bazel.remote.execution.v2.{
  ActionCacheGrpc,
  ActionResult => XActionResult,
  BatchReadBlobsRequest,
  BatchReadBlobsResponse,
  BatchUpdateBlobsRequest,
  BatchUpdateBlobsResponse,
  Compressor,
  ContentAddressableStorageGrpc,
  Digest => XDigest,
  DigestFunction,
  FindMissingBlobsRequest,
  GetActionResultRequest => XGetActionResultRequest,
  OutputFile,
  UpdateActionResultRequest => XUpdateActionResultRequest,
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
import java.net.URI
import java.nio.file.{ Files, Path }
import sbt.util.{
  AbstractActionCacheStore,
  ActionResult,
  Digest,
  DiskActionCacheStore,
  GetActionResultRequest,
  UpdateActionResultRequest,
}
import scala.util.control.NonFatal
import scala.jdk.CollectionConverters.*
import xsbti.{ HashedVirtualFileRef, VirtualFile }

object GrpcActionCacheStore:
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
          pairs.map { case (k, v) =>
            headers.put(k, v)
          }
          applier.apply(headers)
        catch case NonFatal(e) => applier.fail(Status.UNAUTHENTICATED.withCause(e))
  end AuthCallCredentials
end GrpcActionCacheStore

/*
 * https://github.com/bazelbuild/remote-apis/blob/main/build/bazel/remote/execution/v2/remote_execution.proto
 */
class GrpcActionCacheStore(
    channel: ManagedChannel,
    instanceName: String,
    remoteHeaders: List[String],
    disk: DiskActionCacheStore,
) extends AbstractActionCacheStore:
  lazy val creds = GrpcActionCacheStore.AuthCallCredentials(remoteHeaders)
  lazy val acStub0 = ActionCacheGrpc.newBlockingStub(channel)
  lazy val acStub = remoteHeaders match
    case x :: xs => acStub0.withCallCredentials(creds)
    case _       => acStub0
  lazy val casStub0 = ContentAddressableStorageGrpc.newBlockingStub(channel)
  lazy val casStub = remoteHeaders match
    case x :: xs => casStub0.withCallCredentials(creds)
    case _       => casStub0

  override def storeName: String = "remote"

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
   * https://github.com/bazelbuild/remote-apis/blob/9ff14cecffe5287ba337f857731ceadfc2d80de9/build/bazel/remote/execution/v2/remote_execution.proto#L379
   */
  override def putBlobs(blobs: Seq[VirtualFile]): Seq[HashedVirtualFileRef] =
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
      Map(responses.map(res => toDigest(res.getDigest()) -> res): _*)
    blobs.flatMap: blob =>
      val d = Digest(blob)
      if lookupResponse.contains(d) then
        Some(HashedVirtualFileRef.of(blob.id(), d.contentHashStr, d.sizeBytes))
      else None

  /**
   * https://github.com/bazelbuild/remote-apis/blob/9ff14cecffe5287ba337f857731ceadfc2d80de9/build/bazel/remote/execution/v2/remote_execution.proto#L403
   */
  override def syncBlobs(refs: Seq[HashedVirtualFileRef], outputDirectory: Path): Seq[Path] =
    val result = doGetBlobs(refs)
    val blobs = result.getResponsesList().asScala.toList
    val allOk = blobs.forall(_.getStatus().getCode() == 0)
    if allOk then
      // do not assume the responses to come in order
      val lookupResponse: Map[Digest, BatchReadBlobsResponse.Response] =
        Map(blobs.map(res => toDigest(res.getDigest) -> res): _*)
      refs.map: r =>
        val digest = Digest(r)
        val blob = lookupResponse(digest)
        val casFile = disk.putBlob(blob.getData().newInput(), digest)
        val shortPath =
          if r.id.startsWith("${OUT}/") then r.id.drop(7)
          else r.id
        val outPath = outputDirectory.resolve(shortPath)
        Files.createDirectories(outPath.getParent())
        if outPath.toFile().exists() then IO.delete(outPath.toFile())
        Files.createSymbolicLink(outPath, casFile)
        outPath
    else Nil

  /**
   * https://github.com/bazelbuild/remote-apis/blob/96942a2107c702ed3ca4a664f7eeb7c85ba8dc77/build/bazel/remote/execution/v2/remote_execution.proto#L1629
   */
  override def findBlobs(refs: Seq[HashedVirtualFileRef]): Seq[HashedVirtualFileRef] =
    val b = FindMissingBlobsRequest.newBuilder()
    b.setInstanceName(instanceName)
    refs.map: r =>
      b.addBlobDigests(toXDigest(Digest(r)))
    b.setDigestFunction(DigestFunction.Value.SHA256)
    val req = b.build()
    val res = casStub.findMissingBlobs(req)
    val missing = res.getMissingBlobDigestsList().asScala.map(toDigest).toSet
    refs.flatMap: r =>
      if missing(Digest(r)) then None
      else Some(r)

  private def doGetBlobs(refs: Seq[HashedVirtualFileRef]): BatchReadBlobsResponse =
    val b = BatchReadBlobsRequest.newBuilder()
    b.setInstanceName(instanceName)
    refs.map: ref =>
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
    val shortPath =
      if ref.id.startsWith("${OUT}/") then ref.id.drop(7)
      else ref.id
    b.setPath(shortPath)
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
end GrpcActionCacheStore
