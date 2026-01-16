/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import sbt.internal.util.codec.HashedVirtualFileRefFormats
import sjsonnew.{ BasicJsonProtocol, IsoString, JsonFormat }
import xsbti.VirtualFileRef

trait BasicCacheImplicits extends HashedVirtualFileRefFormats { self: BasicJsonProtocol =>

  given basicCache[I: JsonFormat, O: JsonFormat]: Cache[I, O] =
    new BasicCache[I, O]()

  def wrapIn[I, J](using f: I => J, g: J => I, jCache: SingletonCache[J]): SingletonCache[I] =
    new SingletonCache[I] {
      override def read(from: Input): I = g(jCache.read(from))
      override def write(to: Output, value: I) = jCache.write(to, f(value))
    }

  def singleton[T](t: T): SingletonCache[T] =
    SingletonCache.basicSingletonCache(using asSingleton(t))

  given virtualFileRefIsoString: IsoString[VirtualFileRef] =
    IsoString.iso(_.id, VirtualFileRef.of)
}
