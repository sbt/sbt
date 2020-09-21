package net.virtualvoid.sbt.graph

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, File }
import java.util.Base64

import sbinary.{ Format, JavaInput, JavaOutput }
import sjsonnew.{ Builder, Unbuilder }

trait ModuleGraphProtocolCompat {
  implicit def sjsonNewAndShinyTransformAndTranspileAdapterFactoryModuleImplementation[T](implicit format: Format[T]): sjsonnew.JsonFormat[T] =
    new sjsonnew.JsonFormat[T] {
      // note, how this is simpler to write than to learn any sjonnew protocol syntax
      def write[J](obj: T, builder: Builder[J]): Unit = {
        val baos = new ByteArrayOutputStream()
        format.writes(new JavaOutput(baos), obj)
        val str = Base64.getEncoder.encodeToString(baos.toByteArray)
        builder.writeString(str)
      }
      def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): T = {
        val str = unbuilder.readString(jsOpt.get)
        val bais = new ByteArrayInputStream(Base64.getDecoder.decode(str))
        format.reads(new JavaInput(bais))
      }
    }
}
