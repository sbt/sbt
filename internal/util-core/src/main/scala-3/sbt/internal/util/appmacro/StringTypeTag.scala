/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util.appmacro

final class StringTypeTag[A](val key: String):
  override def toString(): String = key
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: StringTypeTag[?] => (this.key == x.key)
    case _                   => false
  })
  override def hashCode: Int = key.##
end StringTypeTag

object StringTypeTag:
  inline given apply[A]: StringTypeTag[A] = ${ applyImpl[A] }

  def manually[A](key: String): StringTypeTag[A] = new StringTypeTag(key)

  import scala.quoted.*
  private def applyImpl[A: Type](using qctx: Quotes): Expr[StringTypeTag[A]] =
    import qctx.reflect.*
    val tpe = TypeRepr.of[A]
    '{ new StringTypeTag[A](${ Expr(tpe.dealias.show) }) }
end StringTypeTag
