/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util
import scala.collection.parallel.CollectionConverters.*
import scala.collection.parallel.ParSeq

private[util] object Par {
  def apply[R](s: Seq[R]): ParSeq[R] = s.par
}
