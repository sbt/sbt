/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util.appmacro

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/** This is used to carry type information in JSON. */
final case class StringTypeTag[A](key: String) {
  override def toString: String = key
}

object StringTypeTag {

  /** Generates a StringTypeTag for any type at compile time. */
  implicit def fast[A]: StringTypeTag[A] = macro impl[A]

  def impl[A: c.WeakTypeTag](c: blackbox.Context): c.Tree = {
    import c.universe._
    val tpe = weakTypeOf[A]
    def typeToString(tpe: Type): String = tpe match {
      case TypeRef(_, sym, args) if args.nonEmpty =>
        val typeCon = tpe.typeSymbol.fullName
        val typeArgs = args map typeToString
        s"""$typeCon[${typeArgs.mkString(",")}]"""
      case _ => tpe.toString
    }

    val key = Literal(Constant(typeToString(tpe)))
    q"new sbt.internal.util.appmacro.StringTypeTag[$tpe]($key)"
  }
}
