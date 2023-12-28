package sbt.util

import sjsonnew.IsoString

opaque type Digest = String

object Digest:
  def apply(s: String): Digest =
    validateString(s)
    s

  private def validateString(s: String): Unit =
    val tokens = s.split("-").toList
    tokens match
      case "md5" :: value :: Nil     => ()
      case "sha1" :: value :: Nil    => ()
      case "sha256" :: value :: Nil  => ()
      case "sha384" :: value :: Nil  => ()
      case "sha512" :: value :: Nil  => ()
      case "murmur3" :: value :: Nil => ()
      case _                         => throw IllegalArgumentException(s"unexpected digest: $s")
    ()

  given IsoString[Digest] = IsoString.iso(x => x, s => s)
end Digest
