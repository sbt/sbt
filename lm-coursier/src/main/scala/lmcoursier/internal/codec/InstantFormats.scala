package lmcoursier.internal.codec

import sjsonnew.*
import java.time.Instant

trait InstantFormats { self: sjsonnew.BasicJsonProtocol =>
  given InstantFormat: JsonFormat[Instant] = new JsonFormat[Instant] {
    def write[J](obj: Instant, builder: Builder[J]): Unit =
      builder.writeString(obj.toString)

    def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): Instant =
      jsOpt match {
        case Some(js) =>
          val str = unbuilder.readString(js)
          Instant.parse(str)
        case None =>
          deserializationError("Expected JString for Instant")
      }
  }
}
