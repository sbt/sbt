package sbt
package internal
package util
package codec

import sjsonnew.{ BasicJsonProtocol, IsoString }
import xsbti.HashedVirtualFileRef

trait HashedVirtualFileRefFormats { self: BasicJsonProtocol =>

  /**
   * A string representation of HashedVirtualFileRef, delimited by `>`.
   */
  def hashedVirtualFileRefToStr(ref: HashedVirtualFileRef): String =
    s"${ref.id}>${ref.contentHashStr}/${ref.sizeBytes}"

  def strToHashedVirtualFileRef(s: String): HashedVirtualFileRef =
    s.split(">").toList match {
      case path :: rest :: Nil =>
        rest.split("/").toList match {
          case hash :: size :: Nil => HashedVirtualFileRef.of(path, hash, size.toLong)
          case _ => throw new RuntimeException(s"invalid HashedVirtualFileRefIsoString $s")
        }
      case _ => throw new RuntimeException(s"invalid HashedVirtualFileRefIsoString $s")
    }

  given hashedVirtualFileRefIsoString: IsoString[HashedVirtualFileRef] =
    IsoString.iso(hashedVirtualFileRefToStr, strToHashedVirtualFileRef)
}
