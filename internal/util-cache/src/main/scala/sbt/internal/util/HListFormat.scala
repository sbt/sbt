package sbt.internal.util

import sjsonnew._
import Types.:+:

trait HListFormat {
  implicit val lnilFormat1: JsonFormat[HNil] = forHNil(HNil)
  implicit val lnilFormat2: JsonFormat[HNil.type] = forHNil(HNil)

  private def forHNil[A <: HNil](hnil: A): JsonFormat[A] = new JsonFormat[A] {
    def write[J](x: A, builder: Builder[J]): Unit = {
      if (builder.state != BuilderState.InArray) builder.beginArray()
      builder.endArray()
    }

    def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): A = {
      if (unbuilder.state == UnbuilderState.InArray) unbuilder.endArray()
      hnil
    }
  }

  implicit def hconsFormat[H, T <: HList](implicit hf: JsonFormat[H], tf: JsonFormat[T]): JsonFormat[H :+: T] =
    new JsonFormat[H :+: T] {
      def write[J](hcons: H :+: T, builder: Builder[J]) = {
        if (builder.state != BuilderState.InArray) builder.beginArray()
        hf.write(hcons.head, builder)
        tf.write(hcons.tail, builder)
      }

      def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]) = jsOpt match {
        case None => HCons(hf.read(None, unbuilder), tf.read(None, unbuilder))
        case Some(js) =>
          if (unbuilder.state != UnbuilderState.InArray) unbuilder.beginArray(js)
          HCons(hf.read(Some(unbuilder.nextElement), unbuilder), tf.read(Some(js), unbuilder))
      }
    }
}
