/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util.appmacro

import scala.reflect.macros.blackbox

object StringTypeTag {
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
    q"new sbt.internal.util.StringTypeTag[$tpe]($key)"
  }
}
