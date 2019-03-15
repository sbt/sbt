/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
import java.lang
import java.nio.file.Path
import java.util.Optional

import sbt.internal.inc.{ EmptyStamp, LastModified, Stamp }
import sbt.io.FileEventMonitor.{ Creation, Deletion, Update }
import sbt.io.{ FileEventMonitor, TypedPath }
import xsbti.compile.analysis.{ Stamp => XStamp }

/**
 * Represents a cache entry for a FileTreeRepository. It can be extended to add user defined
 * data to the FileTreeRepository cache.
 */
trait FileCacheEntry {
  def hash: Option[String]
  def lastModified: Option[Long]
}
object FileCacheEntry {
  trait Event {
    def path: Path
    def previous: Option[FileCacheEntry]
    def current: Option[FileCacheEntry]
  }
  private[sbt] class EventImpl(event: FileEventMonitor.Event[FileCacheEntry]) extends Event {
    override def path: Path = event.entry.typedPath.toPath
    override def previous: Option[FileCacheEntry] = event match {
      case Deletion(entry, _)     => entry.value.toOption
      case Update(previous, _, _) => previous.value.toOption
      case _                      => None
    }
    override def current: Option[FileCacheEntry] = event match {
      case Creation(entry, _)    => entry.value.toOption
      case Update(_, current, _) => current.value.toOption
      case _                     => None
    }
    override def equals(o: Any): Boolean = o match {
      case that: Event =>
        this.path == that.path && this.previous == that.previous && this.current == that.current
      case _ => false
    }
    override def hashCode(): Int =
      ((path.hashCode * 31) ^ previous.hashCode() * 31) ^ current.hashCode()
    override def toString: String = s"Event($path, $previous, $current)"
  }
  private[sbt] def default(typedPath: TypedPath): FileCacheEntry =
    DelegateFileCacheEntry(Stamped.converter(typedPath))
  private[sbt] implicit class FileCacheEntryOps(val e: FileCacheEntry) extends AnyVal {
    private[sbt] def stamp: XStamp = e match {
      case DelegateFileCacheEntry(s) => s
      case _ =>
        e.hash
          .map(Stamp.fromString)
          .orElse(e.lastModified.map(new LastModified(_)))
          .getOrElse(EmptyStamp)
    }
  }

  private implicit class Equiv(val xstamp: XStamp) extends AnyVal {
    def equiv(that: XStamp): Boolean = Stamp.equivStamp.equiv(xstamp, that)
  }
  private case class DelegateFileCacheEntry(private val stamp: XStamp)
      extends FileCacheEntry
      with XStamp {
    override def getValueId: Int = stamp.getValueId
    override def writeStamp(): String = stamp.writeStamp()
    override def getHash: Optional[String] = stamp.getHash
    override def getLastModified: Optional[lang.Long] = stamp.getLastModified
    override def hash: Option[String] = getHash match {
      case h if h.isPresent => Some(h.get)
      case _                => None
    }
    override def lastModified: Option[Long] = getLastModified match {
      case l if l.isPresent => Some(l.get)
      case _                => None
    }
    override def equals(o: Any): Boolean = o match {
      case DelegateFileCacheEntry(thatStamp) => this.stamp equiv thatStamp
      case xStamp: XStamp                    => this.stamp equiv xStamp
      case _                                 => false
    }
    override def hashCode: Int = stamp.hashCode
    override def toString: String = s"FileCacheEntry(hash = $hash, lastModified = $lastModified)"
  }
}
