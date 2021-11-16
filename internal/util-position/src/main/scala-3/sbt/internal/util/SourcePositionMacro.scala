/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import scala.quoted.{ Expr, Quotes, quotes }

abstract class SourcePositionImpl {

  /** Creates a SourcePosition by using the enclosing position of the invocation of this method.
   *
   * @return SourcePosition
   */
  inline def fromEnclosing(): SourcePosition =
    ${ SourcePositionImpl.fromEnclosingImpl }
}

object SourcePositionImpl {

  def fromEnclosingImpl(using Quotes): Expr[SourcePosition] = {
    val x = quotes.reflect.Position.ofMacroExpansion

    '{
      LinePosition(
        path = ${Expr(x.sourceFile.name)},
        startLine = ${Expr(x.startLine + 1)}
      )
    }
  }
}
