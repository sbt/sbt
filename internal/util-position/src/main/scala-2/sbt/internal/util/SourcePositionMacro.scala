/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import scala.annotation.tailrec
import scala.reflect.macros.blackbox
import scala.reflect.internal.util.UndefinedPosition

abstract class SourcePositionImpl {

  /**
   * Creates a SourcePosition by using the enclosing position of the invocation of this method.
   * @return SourcePosition
   */
  def fromEnclosing(): SourcePosition = macro SourcePositionMacro.fromEnclosingImpl
}

final class SourcePositionMacro(val c: blackbox.Context) {
  import c.universe.{ NoPosition => _, _ }

  def fromEnclosingImpl(): Expr[SourcePosition] = {
    val pos = c.enclosingPosition
    if (!pos.isInstanceOf[UndefinedPosition] && pos.line >= 0 && pos.source != null) {
      val f = pos.source.file
      val name = constant[String](ownerSource(f.path, f.name))
      val line = constant[Int](pos.line)
      reify { LinePosition(name.splice, line.splice) }
    } else reify { NoPosition }
  }

  private def ownerSource(path: String, name: String): String = {
    @tailrec def inEmptyPackage(s: Symbol): Boolean =
      s != NoSymbol && (
        s.owner == c.mirror.EmptyPackage
          || s.owner == c.mirror.EmptyPackageClass
          || inEmptyPackage(s.owner)
      )

    c.internal.enclosingOwner match {
      case ec if !ec.isStatic       => name
      case ec if inEmptyPackage(ec) => path
      case ec                       => s"(${ec.fullName}) $name"
    }
  }

  private def constant[T: WeakTypeTag](t: T): Expr[T] = c.Expr[T](Literal(Constant(t)))
}
