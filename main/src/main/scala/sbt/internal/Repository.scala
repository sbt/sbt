/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.util.concurrent.ConcurrentHashMap

import scala.collection.JavaConverters._

/**
 * Represents an abstract cache of values, accessible by a key. The interface is deliberately
 * minimal to give maximum flexibility to the implementation classes. For example, one can construct
 * a cache from a `sbt.io.FileTreeRepository` that automatically registers the paths with the
 * cache (but does not clear the cache on close):
 * {{{
 *  val repository = sbt.io.FileTreeRepository.default(_.getPath)
 *  val fileCache = new Repository[Seq, (Path, Boolean), TypedPath] {
 *    override def get(key: (Path, Boolean)): Seq[TypedPath] = {
 *      val (path, recursive) = key
 *      val depth = if (recursive) Int.MaxValue else 0
 *      repository.register(path, depth)
 *      repository.list(path, depth, AllPass)
 *    }
 *    override def close(): Unit = {}
 *  }
 * }}}
 *
 * @tparam M the container type of the cache. This will most commonly be `Option` or `Seq`.
 * @tparam K the key type
 * @tparam V the value type
 */
trait Repository[M[_], K, V] extends AutoCloseable {
  def get(key: K): M[V]
  override def close(): Unit = {}
}

private[sbt] final class MutableRepository[K, V] extends Repository[Option, K, V] {
  private[this] val map = new ConcurrentHashMap[K, V].asScala
  override def get(key: K): Option[V] = map.get(key)
  def put(key: K, value: V): Unit = this.synchronized {
    map.put(key, value)
    ()
  }
  def remove(key: K): Unit = this.synchronized {
    map.remove(key)
    ()
  }
  override def close(): Unit = this.synchronized {
    map.foreach {
      case (_, v: AutoCloseable) => v.close()
      case _                     =>
    }
    map.clear()
  }
}
