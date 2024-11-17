/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.util.concurrent.ConcurrentHashMap
import sbt.internal.inc.Stamper
import xsbti.{ FileConverter, VirtualFile, VirtualFileRef }
import xsbti.compile.DefinesClass
import xsbti.compile.analysis.{ Stamp => XStamp }
import sbt.internal.inc.Locate

/**
 * Cache based on path and its stamp.
 */
sealed trait VirtualFileValueCache[A] {
  def clear(): Unit
  def get: VirtualFile => A
}

object VirtualFileValueCache {
  def definesClassCache(converter: FileConverter): VirtualFileValueCache[DefinesClass] = {
    apply(converter) { (x: VirtualFile) =>
      if (x.name.toString != "rt.jar") Locate.definesClass(x)
      else (_: String) => false
    }
  }
  def apply[A](converter: FileConverter)(f: VirtualFile => A): VirtualFileValueCache[A] = {
    import collection.concurrent.Map
    import java.util.concurrent.ConcurrentHashMap
    import scala.jdk.CollectionConverters._
    val stampCache: Map[VirtualFileRef, (Long, XStamp)] = new ConcurrentHashMap().asScala
    make(
      Stamper.timeWrap(
        stampCache,
        converter,
        { case (vf: VirtualFile) =>
          Stamper.forContentHash(vf)
        }
      )
    )(f)
  }
  def make[A](stamp: VirtualFile => XStamp)(f: VirtualFile => A): VirtualFileValueCache[A] =
    new VirtualFileValueCache0[A](stamp, f)(using Equiv.universal)
}

private final class VirtualFileValueCache0[A](
    getStamp: VirtualFile => XStamp,
    make: VirtualFile => A
)(using
    equiv: Equiv[XStamp]
) extends VirtualFileValueCache[A] {
  private val backing = new ConcurrentHashMap[VirtualFile, VirtualFileCache]

  def clear(): Unit = backing.clear()
  def get = file => {
    val ifAbsent = new VirtualFileCache(file)
    val cache = backing.putIfAbsent(file, ifAbsent)
    (if (cache eq null) ifAbsent else cache).get()
  }

  private final class VirtualFileCache(file: VirtualFile) {
    private var stampedValue: Option[(XStamp, A)] = None
    def get(): A = synchronized {
      val latest = getStamp(file)
      stampedValue match {
        case Some((stamp, value)) if (equiv.equiv(latest, stamp)) => value
        case _                                                    => update(latest)
      }
    }

    private def update(stamp: XStamp): A = {
      val value = make(file)
      stampedValue = Some((stamp, value))
      value
    }
  }
}
