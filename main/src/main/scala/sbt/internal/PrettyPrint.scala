/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

private[sbt] object PrettyPrint {
  private[sbt] def indent(any: Any, level: Int): String = {
    val i = " " * level
    any.toString.linesIterator.mkString(i, "\n" + i, "")
  }
}
