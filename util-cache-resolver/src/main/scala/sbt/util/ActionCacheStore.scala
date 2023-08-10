package sbt.util

import java.io.InputStream
import sjsonnew.{ JsonReader, JsonWriter }
import sjsonnew.support.scalajson.unsafe.Converter
import sjsonnew.shaded.scalajson.ast.unsafe.JValue

import scala.collection.mutable
import xsbti.{ VirtualFile, VirtualFileRef }

/**
 * An abstration of a remote or local cache store.
 */
trait ActionCacheStore:
  def read[A1: JsonReader](input: ActionInput): Option[ActionValue[A1]]
  def write[A1: JsonWriter](key: ActionInput, value: ActionValue[A1]): Unit
  def readBlobs(refs: Seq[HashedVirtualFileRef]): Seq[VirtualFile]
  def writeBlobs(blobs: Seq[VirtualFile]): Seq[HashedVirtualFileRef]
end ActionCacheStore

class AggregateActionCacheStore(stores: Seq[ActionCacheStore]) extends ActionCacheStore:
  override def read[A1: JsonReader](input: ActionInput): Option[ActionValue[A1]] =
    var result: Option[ActionValue[A1]] = None
    stores.foreach: store =>
      if result.isEmpty then result = store.read[A1](input)
    result

  override def write[A1: JsonWriter](key: ActionInput, value: ActionValue[A1]): Unit =
    stores.foreach: store =>
      store.write[A1](key, value)

  override def readBlobs(refs: Seq[HashedVirtualFileRef]): Seq[VirtualFile] =
    var result: Seq[VirtualFile] = Nil
    stores.foreach: store =>
      if result.isEmpty then result = store.readBlobs(refs)
    result

  override def writeBlobs(blobs: Seq[VirtualFile]): Seq[HashedVirtualFileRef] =
    var result: Seq[HashedVirtualFileRef] = Nil
    stores.foreach: store =>
      val xs = store.writeBlobs(blobs)
      if result.isEmpty then result = xs
    result
end AggregateActionCacheStore

object AggregateActionCacheStore:
  lazy val empty: AggregateActionCacheStore = AggregateActionCacheStore(Nil)
end AggregateActionCacheStore

class InMemoryActionCacheStore extends ActionCacheStore:
  private val underlying: mutable.Map[ActionInput, JValue] = mutable.Map.empty
  private val blobCache: mutable.Map[HashedVirtualFileRef, VirtualFile] = mutable.Map.empty

  def read[A1: JsonReader](input: ActionInput): Option[ActionValue[A1]] =
    underlying
      .get(input)
      .map: j =>
        ActionValue(Converter.fromJsonUnsafe[A1](j), Nil)
  def write[A1: JsonWriter](key: ActionInput, value: ActionValue[A1]): Unit =
    underlying(key) = Converter.toJsonUnsafe(value.value)

  def readBlobs(refs: Seq[HashedVirtualFileRef]): Seq[VirtualFile] =
    refs.map: r =>
      blobCache(r)

  def writeBlobs(blobs: Seq[VirtualFile]): Seq[HashedVirtualFileRef] =
    blobs.map: b =>
      val ref = HashedVirtualFileRef(b.id, b.contentHash)
      blobCache(ref) = b
      ref

  override def toString(): String =
    underlying.toString()
end InMemoryActionCacheStore
