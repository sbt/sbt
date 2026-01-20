/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import scala.annotation.nowarn
import scala.compiletime.{ constValue, erasedValue, error, summonFrom }
import scala.deriving.Mirror
import sjsonnew.{ Builder, JsonFormat, Unbuilder, deserializationError }

/**
 * Compile-time JsonFormat derivation helpers.
 */
object AutoJsonFormat {

  /**
   * Derives a JsonFormat for a product type (typically a case class).
   *
   * This is intentionally strict: derivation succeeds only if a JsonFormat exists
   * for every field type.
   */
  @nowarn("msg=New anonymous class definition will be duplicated at each inline site")
  inline def derived[T](using m: Mirror.ProductOf[T]): JsonFormat[T] =
    new JsonFormat[T] {
      override def write[J](obj: T, builder: Builder[J]): Unit =
        builder.beginObject()
        writeFields[m.MirroredElemTypes, m.MirroredElemLabels, J](
          obj.asInstanceOf[Product],
          0,
          builder,
        )
        builder.endObject()

      override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): T =
        jsOpt match
          case Some(js) =>
            unbuilder.beginObject(js)
            val values = readFields[m.MirroredElemTypes, m.MirroredElemLabels, J](unbuilder)
            unbuilder.endObject()
            m.fromProduct(values)
          case None =>
            deserializationError("Expected JsObject but found None")
    }

  private transparent inline def summonFieldFormat[t, l]: JsonFormat[t] =
    summonFrom {
      case f: JsonFormat[`t`] => f
      case _ =>
        error(
          "Cannot derive JsonFormat. Missing JsonFormat for field '" +
            constValue[l].asInstanceOf[String] +
            "'. Consider using Def.uncached() or providing an explicit JsonFormat."
        )
    }

  private inline def writeFields[Ts <: Tuple, Ls <: Tuple, J](
      p: Product,
      idx: Int,
      builder: Builder[J],
  ): Unit =
    inline erasedValue[Ts] match
      case _: (t *: ts) =>
        inline erasedValue[Ls] match
          case _: (l *: ls) =>
            val label = constValue[l].asInstanceOf[String]
            val value = p.productElement(idx).asInstanceOf[t]
            builder.addField[t](label, value)(using summonFieldFormat[t, l])
            writeFields[ts, ls, J](p, idx + 1, builder)
      case _: EmptyTuple =>
        ()

  private inline def readFields[Ts <: Tuple, Ls <: Tuple, J](unbuilder: Unbuilder[J]): Ts =
    inline erasedValue[Ts] match
      case _: (t *: ts) =>
        inline erasedValue[Ls] match
          case _: (l *: ls) =>
            val label = constValue[l].asInstanceOf[String]
            val head = unbuilder.readField[t](label)(using summonFieldFormat[t, l])
            (head *: readFields[ts, ls, J](unbuilder)).asInstanceOf[Ts]
      case _: EmptyTuple =>
        EmptyTuple.asInstanceOf[Ts]

  /**
   * Fallback JsonFormat that provides helpful error messages
   */
  def fallbackFormat[T](typeName: String): JsonFormat[T] = new JsonFormat[T] {
    def write[J](obj: T, builder: Builder[J]): Unit = {
      throw new UnsupportedOperationException(
        s"""Cannot serialize $typeName. 
           |Consider using Def.uncached() or providing an explicit JsonFormat.
           |For sbt internal types, you may need to add the format to AutoJsonFormats.""".stripMargin
      )
    }

    def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): T = {
      throw new UnsupportedOperationException(
        s"""Cannot deserialize $typeName.
           |Consider using Def.uncached() or providing an explicit JsonFormat.
           |For sbt internal types, you may need to add the format to AutoJsonFormats.""".stripMargin
      )
    }
  }
}
