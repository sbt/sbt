package sbt.internal.util

import scala.reflect.runtime.universe._

/** This is used to carry type information in JSON. */
final case class StringTypeTag[A](key: String) {
  override def toString: String = key
}

object StringTypeTag {
  def apply[A: TypeTag]: StringTypeTag[A] =
    {
      val tag = implicitly[TypeTag[A]]
      val tpe = tag.tpe
      val k = typeToString(tpe)
      // println(tpe.getClass.toString + " " + k)
      StringTypeTag[A](k)
    }
  def typeToString(tpe: Type): String =
    tpe match {
      case ref: TypeRef =>
        if (ref.args.nonEmpty) {
          val typeCon = ref.typeConstructor.typeSymbol.asType.fullName
          val typeArgs = ref.typeArgs map typeToString
          s"""$typeCon[${typeArgs.mkString(",")}]"""
        } else tpe.toString
      case _ => tpe.toString
    }
}
