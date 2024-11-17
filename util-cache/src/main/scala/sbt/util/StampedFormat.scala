/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import scala.reflect.ClassTag

import sjsonnew.{ BasicJsonProtocol, Builder, deserializationError, JsonFormat, Unbuilder }

object StampedFormat extends BasicJsonProtocol {

  def apply[T](format: JsonFormat[T])(using mf: ClassTag[JsonFormat[T]]): JsonFormat[T] = {
    withStamp(stamp(format))(format)
  }

  def withStamp[T, S](stamp: S)(format: JsonFormat[T])(using
      formatStamp: JsonFormat[S],
      equivStamp: Equiv[S]
  ): JsonFormat[T] =
    new JsonFormat[T] {
      override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): T =
        jsOpt match {
          case Some(js) =>
            val stampedLength = unbuilder.beginArray(js)
            if (stampedLength != 2)
              sys.error(s"Expected JsArray of size 2, found JsArray of size $stampedLength.")
            val readStamp = unbuilder.nextElement
            val readValue = unbuilder.nextElement
            val actualStamp = formatStamp.read(Some(readStamp), unbuilder)
            if (equivStamp.equiv(actualStamp, stamp)) format.read(Some(readValue), unbuilder)
            else sys.error(s"Incorrect stamp. Expected: $stamp, Found: $readStamp")

          case None =>
            deserializationError("Expected JsArray but found None.")
        }

      override def write[J](obj: T, builder: Builder[J]): Unit = {
        builder.beginArray()
        formatStamp.write(stamp, builder)
        format.write(obj, builder)
        builder.endArray()
      }
    }

  private def stamp[T](format: JsonFormat[T])(using mf: ClassTag[JsonFormat[T]]): Int =
    typeHash(using mf)

  private def typeHash[T](using mf: ClassTag[T]) = mf.toString.hashCode

}
