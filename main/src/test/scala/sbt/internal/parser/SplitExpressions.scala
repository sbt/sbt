/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal
package parser

import java.io.File

import sbt.internal.util.LineRange

object SplitExpressions {
  type SplitExpression = (File, Seq[String]) => (Seq[(String, Int)], Seq[(String, LineRange)])
}
