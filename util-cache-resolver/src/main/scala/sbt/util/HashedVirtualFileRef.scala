package sbt.util

import xsbti.VirtualFileRef

/**
 * HashedVirtualFileRef represents a pair of logical file name "id" and a content hash.
 * We use this as a safe reference to a file.
 */
class HashedVirtualFileRef(_id: String, hash: Long) extends VirtualFileRef:
  def contentHash: Long = hash
  override def id: String = _id
  override def name(): String = id.substring(id.lastIndexOf('/') + 1)
  override def names(): Array[String] = id.split("/")
  override def equals(o: Any): Boolean =
    o match {
      case o: HashedVirtualFileRef => this.id == o.id && this.contentHash == o.contentHash
      case _                       => false
    }
  override def hashCode(): Int = (_id, hash).##
  override def toString(): String = s"HashedVirtualFileRef(${_id}, $hash)"
end HashedVirtualFileRef
