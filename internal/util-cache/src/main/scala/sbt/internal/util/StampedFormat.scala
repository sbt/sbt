package sbt.internal.util

import scala.reflect.Manifest

import sbt.datatype.IntFormat

import sjsonnew.{ Builder, deserializationError, JsonFormat, Unbuilder }

object StampedFormat extends IntFormat {

  def apply[T](format: JsonFormat[T])(implicit mf: Manifest[JsonFormat[T]]): JsonFormat[T] = {
    withStamp(stamp(format))(format)
  }

  def withStamp[T, S](stamp: S)(format: JsonFormat[T])(implicit formatStamp: JsonFormat[S], equivStamp: Equiv[S]): JsonFormat[T] =
    new JsonFormat[T] {
      override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): T =
        jsOpt match {
          case Some(js) =>
            unbuilder.extractArray(js) match {
              case Vector(readStamp, readValue) =>
                val actualStamp = formatStamp.read(Some(readStamp), unbuilder)
                if (equivStamp.equiv(actualStamp, stamp)) format.read(Some(readValue), unbuilder)
                else sys.error(s"Incorrect stamp. Expected: $stamp, Found: $readStamp")

              case other =>
                deserializationError(s"Expected JsArray of size 2, but found JsArray of size ${other.size}")
            }

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
  private def stamp[T](format: JsonFormat[T])(implicit mf: Manifest[JsonFormat[T]]): Int = typeHash(mf)
  private def typeHash[T](implicit mf: Manifest[T]) = mf.toString.hashCode

}