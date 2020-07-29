/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import scala.language.experimental.macros
import scala.reflect.runtime.universe._

/** This is used to carry type information in JSON. */
final case class StringTypeTag[A](key: String) {
  override def toString: String = key
}

object StringTypeTag {

  /** Generates a StringTypeTag for any type at compile time. */
  implicit def fast[A]: StringTypeTag[A] = macro appmacro.StringTypeTag.impl[A]
  @deprecated("Prefer macro generated StringTypeTag", "1.4.0")
  def apply[A: TypeTag]: StringTypeTag[A] =
    synchronized {
      def doApply: StringTypeTag[A] = {
        val tag = implicitly[TypeTag[A]]
        val tpe = tag.tpe
        val k = typeToString(tpe)
        // println(tpe.getClass.toString + " " + k)
        StringTypeTag[A](k)
      }
      def retry(n: Int): StringTypeTag[A] =
        try {
          doApply
        } catch {
          case e: NullPointerException =>
            if (n < 1) throw new RuntimeException("NPE in StringTypeTag", e)
            else {
              Thread.sleep(1)
              retry(n - 1)
            }
        }
      retry(3)
    }

  @deprecated("Prefer macro generated StringTypeTag", "1.4.0")
  def typeToString(tpe: Type): String =
    tpe match {
      case TypeRef(_, sym, args) =>
        if (args.nonEmpty) {
          val typeCon = tpe.typeSymbol.fullName
          val typeArgs = args map typeToString
          s"""$typeCon[${typeArgs.mkString(",")}]"""
        } else tpe.toString
      case _ => tpe.toString
    }
}
