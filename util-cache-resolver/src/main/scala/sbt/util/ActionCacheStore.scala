package sbt.util

import java.io.InputStream
import sjsonnew.{ JsonReader, JsonWriter }
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
