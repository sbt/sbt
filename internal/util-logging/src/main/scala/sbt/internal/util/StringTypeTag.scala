package sbt.internal.util

import scala.reflect.runtime.universe._

/** This is used to carry type information in JSON. */
final case class StringTypeTag[A](key: String) {
  override def toString: String = key
}

object StringTypeTag {
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
