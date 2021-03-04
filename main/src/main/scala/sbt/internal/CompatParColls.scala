/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import scala.annotation.nowarn

// https://github.com/scala/scala-parallel-collections/issues/22
private[sbt] object CompatParColls {
  @nowarn
  val Converters = {
    import Compat._
    {
      import scala.collection.parallel._
      CollectionConverters
    }
  }
  object Compat {
    object CollectionConverters
  }
}
