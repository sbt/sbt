/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package parser

import sbt.internal.util.LineRange
import xsbti.VirtualFileRef

object SplitExpressions:
  type SplitExpression =
    (VirtualFileRef, Seq[String]) => (Seq[(String, Int)], Seq[(String, LineRange)])
end SplitExpressions
