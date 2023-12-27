package sbt.util

import java.io.InputStream
import java.nio.file.{ Files, Path }
import sjsonnew.*
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter, Parser }
import sjsonnew.shaded.scalajson.ast.unsafe.JValue

import scala.collection.mutable
import scala.reflect.ClassTag
import scala.util.control.NonFatal
import sbt.internal.util.PlainVirtualFile1
import sbt.io.IO
import sbt.io.syntax.*
import xsbti.{ HashedVirtualFileRef, PathBasedFile, VirtualFile, VirtualFileRef }
import sbt.nio.file.FileAttributes

/**
 * An abstration of a remote or local cache store.
 */
trait ActionCacheStore:
  /**
   * Put a value and blobs to the cache store for later retrieval,
   * based on the key input.
   */
  def put[A1: ClassTag: JsonFormat](
      key: ActionInput,
      value: A1,
      blobs: Seq[VirtualFile],
  ): ActionResult[A1]

  /**
   * Get the value for the key from the cache store.
   */
  def get[A1: ClassTag: JsonFormat](key: ActionInput): Option[ActionResult[A1]]

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
  override def get[A1: ClassTag: JsonFormat](input: ActionInput): Option[ActionResult[A1]] =
    var result: Option[ActionResult[A1]] = None
    stores.foreach: store =>
      if result.isEmpty then result = store.get[A1](input)
    result

  override def put[A1: ClassTag: JsonFormat](
      key: ActionInput,
      value: A1,
      blobs: Seq[VirtualFile],
  ): ActionResult[A1] =
    var result: Option[ActionResult[A1]] = None
    stores.foreach: store =>
      val v = store.put[A1](key, value, blobs)
      if result.isEmpty then result = Some(v)
    result.get

  override def putBlobs(blobs: Seq[VirtualFile]): Seq[HashedVirtualFileRef] =
    var result: Seq[HashedVirtualFileRef] = Nil
    stores.foreach: store =>
      val xs = store.putBlobs(blobs)
      if result.isEmpty then result = xs
    result

  override def getBlobs(refs: Seq[HashedVirtualFileRef]): Seq[VirtualFile] =
    var result: Seq[VirtualFile] = Nil
    stores.foreach: store =>
      if result.isEmpty then result = store.getBlobs(refs)
    result

  override def syncBlobs(refs: Seq[HashedVirtualFileRef], outputDirectory: Path): Seq[Path] =
    var result: Seq[Path] = Nil
    stores.foreach: store =>
      if result.isEmpty then result = store.syncBlobs(refs, outputDirectory)
    result
end AggregateActionCacheStore

object AggregateActionCacheStore:
  lazy val empty: AggregateActionCacheStore = AggregateActionCacheStore(Nil)
end AggregateActionCacheStore

class InMemoryActionCacheStore extends ActionCacheStore:
  private val underlying: mutable.Map[ActionInput, JValue] = mutable.Map.empty

  override def get[A1: ClassTag: JsonFormat](input: ActionInput): Option[ActionResult[A1]] =
    underlying
      .get(input)
      .map: j =>
        Converter.fromJsonUnsafe[ActionResult[A1]](j)

  override def put[A1: ClassTag: JsonFormat](
      key: ActionInput,
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

  override def get[A1: ClassTag: JsonFormat](input: ActionInput): Option[ActionResult[A1]] =
    val acFile = acBase.toFile / input.inputHash
    if acFile.exists then
      val str = IO.read(acFile)
      val json = Parser.parseUnsafe(str)
      try
        val value = Converter.fromJsonUnsafe[ActionResult[A1]](json)
        Some(value)
      catch case NonFatal(_) => None
    else None

  override def put[A1: ClassTag: JsonFormat](
      key: ActionInput,
      value: A1,
      blobs: Seq[VirtualFile],
  ): ActionResult[A1] =
    val acFile = acBase.toFile / key.inputHash
    val refs = putBlobs(blobs)
    val v = ActionResult(value, refs)
    val json = Converter.toJsonUnsafe(v)
    IO.write(acFile, CompactPrinter(json))
    v

  override def putBlobs(blobs: Seq[VirtualFile]): Seq[HashedVirtualFileRef] =
    blobs.map: (b: VirtualFile) =>
      val outFile = casBase.toFile / b.contentHashStr
      IO.transfer(b.input, outFile)
      (b: HashedVirtualFileRef)

  override def getBlobs(refs: Seq[HashedVirtualFileRef]): Seq[VirtualFile] =
    refs.flatMap: r =>
      val casFile = casBase.toFile / r.contentHashStr
      if casFile.exists then
        r match
          case p: PathBasedFile => Some(p)
          case _                => None
      else None

  override def syncBlobs(refs: Seq[HashedVirtualFileRef], outputDirectory: Path): Seq[Path] =
    refs.flatMap: r =>
      val casFile = casBase.toFile / r.contentHashStr
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
