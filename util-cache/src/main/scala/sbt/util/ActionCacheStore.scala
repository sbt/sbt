package sbt.util

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.file.{ Files, Path }
import sjsonnew.*
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter, Parser }
import sjsonnew.shaded.scalajson.ast.unsafe.JValue

import scala.collection.mutable
import scala.util.control.NonFatal
import sbt.io.IO
import sbt.io.syntax.*
import sbt.internal.util.StringVirtualFile1
import sbt.internal.util.codec.ActionResultCodec.given
import xsbti.{ HashedVirtualFileRef, PathBasedFile, VirtualFile }
import java.io.InputStream

/**
 * An abstraction of a remote or local cache store.
 */
trait ActionCacheStore:
  /**
   * A named used to identify the cache store.
   */
  def storeName: String

  /**
   * Put a value and blobs to the cache store for later retrieval,
   * based on the `actionDigest`.
   */
  def put(request: UpdateActionResultRequest): Either[Throwable, ActionResult]

  /**
   * Get the value for the key from the cache store.
   * `inlineContentPaths` - paths whose contents would be inlined.
   */
  def get(request: GetActionResultRequest): Either[Throwable, ActionResult]

  /**
   * Put VirtualFile blobs to the cache store for later retrieval.
   */
  def putBlobs(blobs: Seq[VirtualFile]): Seq[HashedVirtualFileRef]

  /**
   * Materialize blobs to the output directory.
   */
  def syncBlobs(refs: Seq[HashedVirtualFileRef], outputDirectory: Path): Seq[Path]

  /**
   * Find if blobs are present in the storage.
   */
  def findBlobs(refs: Seq[HashedVirtualFileRef]): Seq[HashedVirtualFileRef]
end ActionCacheStore

trait AbstractActionCacheStore extends ActionCacheStore:
  def putBlobsIfNeeded(blobs: Seq[VirtualFile]): Seq[HashedVirtualFileRef] =
    val found = findBlobs(blobs).toSet
    val missing = blobs.flatMap: blob =>
      val ref: HashedVirtualFileRef = blob
      if found.contains(ref) then None
      else Some(blob)
    val combined = putBlobs(missing).toSet ++ found
    blobs.flatMap: blob =>
      val ref: HashedVirtualFileRef = blob
      if combined.contains(ref) then Some(ref)
      else None

  def notFound: Throwable =
    new RuntimeException("not found")
end AbstractActionCacheStore

/**
 * An aggregate ActionCacheStore.
 */
class AggregateActionCacheStore(stores: Seq[ActionCacheStore]) extends AbstractActionCacheStore:
  extension [A1](xs: Seq[A1])
    // unlike collectFirst this accepts A1 => Seq[A2]
    inline def collectFirst2[A2](f: A1 => Seq[A2], size: Int): Seq[A2] =
      xs.foldLeft(Seq.empty[A2]): (res, x) =>
        if res.size == size then res else f(x)

  override def storeName: String = "aggregate"

  override def get(request: GetActionResultRequest): Either[Throwable, ActionResult] =
    // unlike collectFirst we operate on A1 => Option[A2]
    stores.foldLeft(Left(notFound): Either[Throwable, ActionResult]): (res, store) =>
      if res.isRight then res
      else store.get(request)

  override def put(request: UpdateActionResultRequest): Either[Throwable, ActionResult] =
    stores
      .foldLeft(Left(notFound): Either[Throwable, ActionResult]): (res, store) =>
        // put the value into all stores
        val v = store.put(request)
        res.orElse(v)

  override def putBlobs(blobs: Seq[VirtualFile]): Seq[HashedVirtualFileRef] =
    stores.foldLeft(Seq.empty[HashedVirtualFileRef]): (res, store) =>
      // put the blobs in all stores
      val xs = store.putBlobs(blobs)
      if res.isEmpty then xs else res

  override def syncBlobs(refs: Seq[HashedVirtualFileRef], outputDirectory: Path): Seq[Path] =
    stores.collectFirst2(_.syncBlobs(refs, outputDirectory), refs.size)

  override def findBlobs(refs: Seq[HashedVirtualFileRef]): Seq[HashedVirtualFileRef] =
    stores.collectFirst2(_.findBlobs(refs), refs.size)
end AggregateActionCacheStore

object AggregateActionCacheStore:
  lazy val empty: AggregateActionCacheStore = AggregateActionCacheStore(Nil)
end AggregateActionCacheStore

class InMemoryActionCacheStore extends AbstractActionCacheStore:
  private val underlying: mutable.Map[Digest, JValue] = mutable.Map.empty
  private val blobCache: mutable.Map[String, VirtualFile] = mutable.Map.empty

  override def storeName: String = "in-memory"
  override def get(request: GetActionResultRequest): Either[Throwable, ActionResult] =
    val optResult = underlying
      .get(request.actionDigest)
      .flatMap: j =>
        try
          val value = Converter.fromJsonUnsafe[ActionResult](j)
          if request.inlineOutputFiles.isEmpty then Some(value)
          else
            val inlineRefs = request.inlineOutputFiles.map: path =>
              value.outputFiles.find(_.id == path).get
            val contents = getBlobs(inlineRefs).toVector.map: b =>
              ByteBuffer.wrap(IO.readBytes(b.input))
            Some(value.withContents(contents))
        catch case NonFatal(_) => None
    optResult match
      case Some(r) => Right(r)
      case None    => Left(notFound)

  override def put(request: UpdateActionResultRequest): Either[Throwable, ActionResult] =
    val refs = putBlobsIfNeeded(request.outputFiles).toVector
    val v = ActionResult(refs, storeName)
    val json = Converter.toJsonUnsafe(v)
    underlying(request.actionDigest) = json
    Right(v)

  override def putBlobs(blobs: Seq[VirtualFile]): Seq[HashedVirtualFileRef] =
    blobs.map: (b: VirtualFile) =>
      blobCache(b.contentHashStr()) = b
      (b: HashedVirtualFileRef)

  // we won't keep the blobs in-memory so return Nil
  private def getBlobs(refs: Seq[HashedVirtualFileRef]): Seq[VirtualFile] =
    refs.map: ref =>
      blobCache(ref.contentHashStr())

  // we won't keep the blobs in-memory so return Nil
  // to implement this correctly, we'd have to grab the content from the original file
  override def syncBlobs(refs: Seq[HashedVirtualFileRef], outputDirectory: Path): Seq[Path] =
    Nil

  override def findBlobs(refs: Seq[HashedVirtualFileRef]): Seq[HashedVirtualFileRef] =
    refs.flatMap: r =>
      if blobCache.contains(r.contentHashStr()) then Some(r)
      else None

  override def toString(): String =
    underlying.toString()
end InMemoryActionCacheStore

class DiskActionCacheStore(base: Path) extends AbstractActionCacheStore:
  lazy val casBase: Path = {
    val dir = base.resolve("cas")
    IO.createDirectory(dir.toFile)
    dir
  }

  lazy val acBase: Path = {
    val dir = base.resolve("ac")
    IO.createDirectory(dir.toFile)
    dir
  }

  override def storeName: String = "disk"
  override def get(request: GetActionResultRequest): Either[Throwable, ActionResult] =
    val acFile = acBase.toFile / request.actionDigest.toString.replace("/", "-")
    if acFile.exists then
      val str = IO.read(acFile)
      val json = Parser.parseUnsafe(str)
      try
        val value = Converter.fromJsonUnsafe[ActionResult](json)
        if request.inlineOutputFiles.isEmpty then Right(value)
        else
          val inlineRefs = request.inlineOutputFiles.map: path =>
            value.outputFiles.find(_.id == path).get
          val contents = getBlobs(inlineRefs).toVector.map: b =>
            ByteBuffer.wrap(IO.readBytes(b.input))
          Right(value.withContents(contents))
      catch case NonFatal(e) => Left(e)
    else Left(notFound)

  override def put(request: UpdateActionResultRequest): Either[Throwable, ActionResult] =
    try
      val acFile = acBase.toFile / request.actionDigest.toString.replace("/", "-")
      val refs = putBlobsIfNeeded(request.outputFiles).toVector
      val v = ActionResult(refs, storeName)
      val json = Converter.toJsonUnsafe(v)
      IO.write(acFile, CompactPrinter(json))
      Right(v)
    catch case NonFatal(e) => Left(e)

  override def putBlobs(blobs: Seq[VirtualFile]): Seq[HashedVirtualFileRef] =
    blobs.map: (b: VirtualFile) =>
      putBlob(b.input, Digest(b))
      (b: HashedVirtualFileRef)

  def toCasFile(digest: Digest): Path =
    (casBase.toFile / digest.toString.replace("/", "-")).toPath()

  def putBlob(input: InputStream, digest: Digest): Path =
    val casFile = toCasFile(digest)
    IO.transfer(input, casFile.toFile())
    casFile

  def putBlob(input: ByteBuffer, digest: Digest): Path =
    val casFile = toCasFile(digest)
    input.flip()
    val file = RandomAccessFile(casFile.toFile(), "rw")
    try
      file.getChannel().write(input)
      casFile
    finally file.close()

  private def getBlobs(refs: Seq[HashedVirtualFileRef]): Seq[VirtualFile] =
    refs.flatMap: r =>
      val casFile = toCasFile(Digest(r))
      if casFile.toFile().exists then
        r match
          case p: PathBasedFile => Some(p)
          case _ =>
            val content = IO.read(casFile.toFile())
            Some(StringVirtualFile1(r.id, content))
      else None

  override def syncBlobs(refs: Seq[HashedVirtualFileRef], outputDirectory: Path): Seq[Path] =
    refs.flatMap: r =>
      val casFile = toCasFile(Digest(r))
      if casFile.toFile().exists then Some(syncFile(r, casFile, outputDirectory))
      else None

  def syncFile(ref: HashedVirtualFileRef, casFile: Path, outputDirectory: Path): Path =
    val shortPath =
      if ref.id.startsWith("${OUT}/") then ref.id.drop(7)
      else ref.id
    val d = Digest(ref)
    def symlinkAndNotify(outPath: Path): Path =
      Files.createDirectories(outPath.getParent())
      val result = Files.createSymbolicLink(outPath, casFile)
      // after(result)
      result
    outputDirectory.resolve(shortPath) match
      case p if !p.toFile().exists()    => symlinkAndNotify(p)
      case p if Digest.sameDigest(p, d) => p
      case p =>
        IO.delete(p.toFile())
        symlinkAndNotify(p)

  override def findBlobs(refs: Seq[HashedVirtualFileRef]): Seq[HashedVirtualFileRef] =
    refs.flatMap: r =>
      val casFile = toCasFile(Digest(r))
      if casFile.toFile().exists then Some(r)
      else None
end DiskActionCacheStore
