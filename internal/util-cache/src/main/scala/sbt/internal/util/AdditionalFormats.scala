package sbt.internal.util

import sbt.datatype.StringFormat
import sbt.internal.util.Types.:+:

import sjsonnew.{ Builder, deserializationError, JsonFormat, Unbuilder }
import sjsonnew.BasicJsonProtocol.{ wrap, asSingleton }

import java.io.File

import java.net.{ URI, URL }

trait URIFormat { self: StringFormat =>
  implicit def URIFormat: JsonFormat[URI] = wrap(_.toString, new URI(_: String))
}

trait URLFormat { self: StringFormat =>
  implicit def URLFormat: JsonFormat[URL] = wrap(_.toString, new URL(_: String))
}

trait FileFormat { self: StringFormat =>
  implicit def FileFormat: JsonFormat[File] = wrap(_.toString, new File(_: String))
}

trait HListFormat {
  implicit def HConsFormat[H: JsonFormat, T <: HList: JsonFormat]: JsonFormat[H :+: T] =
    new JsonFormat[H :+: T] {
      override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): H :+: T =
        jsOpt match {
          case Some(js) =>
            unbuilder.beginObject(js)
            val h = unbuilder.readField[H]("h")
            val t = unbuilder.readField[T]("t")
            unbuilder.endObject()

            HCons(h, t)

          case None =>
            deserializationError("Expect JValue but found None")
        }

      override def write[J](obj: H :+: T, builder: Builder[J]): Unit = {
        builder.beginObject()
        builder.addField("h", obj.head)
        builder.addField("t", obj.tail)
        builder.endObject()
      }
    }

  implicit val HNilFormat: JsonFormat[HNil] = asSingleton(HNil)

}
