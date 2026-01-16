/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import scala.quoted.{ Expr, Quotes, quotes }

abstract class SourcePositionImpl {

  /**
   * Creates a SourcePosition by using the enclosing position of the invocation of this method.
   *
   * @return SourcePosition
   */
  inline def fromEnclosing(): SourcePosition =
    ${ SourcePositionImpl.fromEnclosingImpl }
}

object SourcePositionImpl {

  def fromEnclosingImpl(using Quotes): Expr[SourcePosition] = {
    val pos = quotes.reflect.Position.ofMacroExpansion
    if pos.startLine >= 0 then
      '{
        LinePosition(
          path = ${ Expr(pos.sourceFile.name) },
          startLine = ${ Expr(pos.startLine) }
        ).withSourceCode(${ Expr(pos.sourceCode) })
      }
    else '{ NoPosition }
  }
}
