package sbt.util

import java.nio.file.{ Files, Path }
import sjsonnew.*
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter, Parser }
import sjsonnew.shaded.scalajson.ast.unsafe.JValue

import scala.collection.mutable
import scala.reflect.ClassTag
import scala.util.control.NonFatal
import sbt.io.IO
import sbt.io.syntax.*
import xsbti.{ HashedVirtualFileRef, PathBasedFile, VirtualFile }

/**
 * An abstraction of a remote or local cache store.
 */
trait ActionCacheStore:
  /**
   * Put a value and blobs to the cache store for later retrieval,
   * based on the `actionDigest`.
   */
  def put[A1: ClassTag: JsonFormat](
      actionDigest: Digest,
      value: A1,
      blobs: Seq[VirtualFile],
  ): ActionResult[A1]

  /**
   * Get the value for the key from the cache store.
   */
  def get[A1: ClassTag: JsonFormat](input: Digest): Option[ActionResult[A1]]

  /**
   * Put VirtualFile blobs to the cache store for later retrieval.
   */
  def putBlobs(blobs: Seq[VirtualFile]): Seq[HashedVirtualFileRef]

  /**
   * Get blobs from the cache store.
   */
  def getBlobs(refs: Seq[HashedVirtualFileRef]): Seq[VirtualFile]

  /**
   * Materialize blobs to the output directory.
   */
  def syncBlobs(refs: Seq[HashedVirtualFileRef], outputDirectory: Path): Seq[Path]
end ActionCacheStore

class AggregateActionCacheStore(stores: Seq[ActionCacheStore]) extends ActionCacheStore:
  extension [A1](xs: Seq[A1])
    // unlike collectFirst this accepts A1 => Option[A2]
    inline def collectFirst1[A2](f: A1 => Option[A2]): Option[A2] =
      xs.foldLeft(Option.empty[A2]): (res, x) =>
        res.orElse(f(x))

    // unlike collectFirst this accepts A1 => Seq[A2]
    inline def collectFirst2[A2](f: A1 => Seq[A2]): Seq[A2] =
      xs.foldLeft(Seq.empty[A2]): (res, x) =>
        if res.isEmpty then f(x) else res

  override def get[A1: ClassTag: JsonFormat](input: Digest): Option[ActionResult[A1]] =
    stores.collectFirst1(_.get[A1](input))

  override def put[A1: ClassTag: JsonFormat](
      actionDigest: Digest,
      value: A1,
      blobs: Seq[VirtualFile],
  ): ActionResult[A1] =
    (stores
      .foldLeft(Option.empty[ActionResult[A1]]): (res, store) =>
        // put the value into all stores
        val v = store.put[A1](actionDigest, value, blobs)
        res.orElse(Some(v))
      )
      .get

  override def putBlobs(blobs: Seq[VirtualFile]): Seq[HashedVirtualFileRef] =
    stores.foldLeft(Seq.empty[HashedVirtualFileRef]): (res, store) =>
      // put the blobs in all stores
      val xs = store.putBlobs(blobs)
      if res.isEmpty then xs else res

  override def getBlobs(refs: Seq[HashedVirtualFileRef]): Seq[VirtualFile] =
    stores.collectFirst2(_.getBlobs(refs))

  override def syncBlobs(refs: Seq[HashedVirtualFileRef], outputDirectory: Path): Seq[Path] =
    stores.collectFirst2(_.syncBlobs(refs, outputDirectory))
end AggregateActionCacheStore

object AggregateActionCacheStore:
  lazy val empty: AggregateActionCacheStore = AggregateActionCacheStore(Nil)
end AggregateActionCacheStore

class InMemoryActionCacheStore extends ActionCacheStore:
  private val underlying: mutable.Map[Digest, JValue] = mutable.Map.empty

  override def get[A1: ClassTag: JsonFormat](input: Digest): Option[ActionResult[A1]] =
    underlying
      .get(input)
      .map: j =>
        Converter.fromJsonUnsafe[ActionResult[A1]](j)

  override def put[A1: ClassTag: JsonFormat](
      key: Digest,
      value: A1,
      blobs: Seq[VirtualFile],
  ): ActionResult[A1] =
    val refs = putBlobs(blobs)
    val v = ActionResult(value, refs)
    val json = Converter.toJsonUnsafe(v)
    underlying(key) = json
    v

  override def putBlobs(blobs: Seq[VirtualFile]): Seq[HashedVirtualFileRef] =
    blobs.map: (b: VirtualFile) =>
      (b: HashedVirtualFileRef)

  // we won't keep the blobs in-memory so return Nil
  override def getBlobs(refs: Seq[HashedVirtualFileRef]): Seq[VirtualFile] =
    Nil

  // we won't keep the blobs in-memory so return Nil
  // to implement this correctly, we'd have to grab the content from the original file
  override def syncBlobs(refs: Seq[HashedVirtualFileRef], outputDirectory: Path): Seq[Path] =
    Nil

  override def toString(): String =
    underlying.toString()
end InMemoryActionCacheStore

class DiskActionCacheStore(base: Path) extends ActionCacheStore:
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

  override def get[A1: ClassTag: JsonFormat](input: Digest): Option[ActionResult[A1]] =
    val acFile = acBase.toFile / input.toString
    if acFile.exists then
      val str = IO.read(acFile)
      val json = Parser.parseUnsafe(str)
      try
        val value = Converter.fromJsonUnsafe[ActionResult[A1]](json)
        Some(value)
      catch case NonFatal(_) => None
    else None

  override def put[A1: ClassTag: JsonFormat](
      key: Digest,
      value: A1,
      blobs: Seq[VirtualFile],
  ): ActionResult[A1] =
    val acFile = acBase.toFile / key.toString
    val refs = putBlobs(blobs)
    val v = ActionResult(value, refs)
    val json = Converter.toJsonUnsafe(v)
    IO.write(acFile, CompactPrinter(json))
    v

  override def putBlobs(blobs: Seq[VirtualFile]): Seq[HashedVirtualFileRef] =
    blobs.map: (b: VirtualFile) =>
      val outFile = casBase.toFile / Digest(b.contentHashStr).toString
      IO.transfer(b.input, outFile)
      (b: HashedVirtualFileRef)

  override def getBlobs(refs: Seq[HashedVirtualFileRef]): Seq[VirtualFile] =
    refs.flatMap: r =>
      val casFile = casBase.toFile / Digest(r.contentHashStr).toString
      if casFile.exists then
        r match
          case p: PathBasedFile => Some(p)
          case _                => None
      else None

  override def syncBlobs(refs: Seq[HashedVirtualFileRef], outputDirectory: Path): Seq[Path] =
    refs.flatMap: r =>
      val casFile = casBase.toFile / Digest(r.contentHashStr).toString
      if casFile.exists then
        val shortPath =
          if r.id.startsWith("${OUT}/") then r.id.drop(7)
          else r.id
        val outPath = outputDirectory.resolve(shortPath)
        Files.createDirectories(outPath.getParent())
        if outPath.toFile().exists() then IO.delete(outPath.toFile())
        Some(Files.createSymbolicLink(outPath, casFile.toPath))
      else None
end DiskActionCacheStore
