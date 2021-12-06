/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import scala.collection.JavaConverters._
private[util] class WrappedMap[K, V](val jmap: java.util.Map[K, V]) extends Map[K, V] {
  def removed(key: K): scala.collection.immutable.Map[K, V] = jmap.asScala.toMap.removed(key)
  def updated[V1 >: V](key: K, value: V1): scala.collection.immutable.Map[K, V1] =
    jmap.asScala.toMap.updated(key, value)

  def get(key: K): Option[V] = Option(jmap.get(key))
  def iterator: Iterator[(K, V)] = jmap.asScala.iterator
}
