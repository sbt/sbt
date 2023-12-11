/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import sjsonnew.{ BasicJsonProtocol, IsoString, IsoStringLong, JsonFormat }
import xsbti.{ HashedVirtualFileRef, VirtualFileRef }

trait BasicCacheImplicits { self: BasicJsonProtocol =>

  implicit def basicCache[I: JsonFormat, O: JsonFormat]: Cache[I, O] =
    new BasicCache[I, O]()

  def wrapIn[I, J](implicit f: I => J, g: J => I, jCache: SingletonCache[J]): SingletonCache[I] =
    new SingletonCache[I] {
      override def read(from: Input): I = g(jCache.read(from))
      override def write(to: Output, value: I) = jCache.write(to, f(value))
    }

  def singleton[T](t: T): SingletonCache[T] =
    SingletonCache.basicSingletonCache(asSingleton(t))

  /**
   * A string representation of HashedVirtualFileRef, delimited by `>`.
   */
  def hashedVirtualFileRefToStr(ref: HashedVirtualFileRef): String =
    s"${ref.id}>${ref.contentHashStr}"

  def strToHashedVirtualFileRef(s: String): HashedVirtualFileRef =
    s.split(">").toList match {
      case path :: hash :: Nil => HashedVirtualFileRef.of(path, hash)
      case _ => throw new RuntimeException(s"invalid HashedVirtualFileRefIsoString $s")
    }

  implicit lazy val virtualFileRefIsoString: IsoString[VirtualFileRef] =
    IsoString.iso(_.id, VirtualFileRef.of)

  implicit lazy val hashedVirtualFileRefIsoString: IsoString[HashedVirtualFileRef] =
    IsoString.iso(hashedVirtualFileRefToStr, strToHashedVirtualFileRef)
}
