/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait JavacOptionsItemFormats { self: sbt.internal.bsp.codec.BuildTargetIdentifierFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val JavacOptionsItemFormat: JsonFormat[sbt.internal.bsp.JavacOptionsItem] = new JsonFormat[sbt.internal.bsp.JavacOptionsItem] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.JavacOptionsItem = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val target = unbuilder.readField[sbt.internal.bsp.BuildTargetIdentifier]("target")
      val options = unbuilder.readField[Vector[String]]("options")
      val classpath = unbuilder.readField[Vector[java.net.URI]]("classpath")
      val classDirectory = unbuilder.readField[Option[java.net.URI]]("classDirectory")
      unbuilder.endObject()
      sbt.internal.bsp.JavacOptionsItem(target, options, classpath, classDirectory)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.JavacOptionsItem, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("target", obj.target)
    builder.addField("options", obj.options)
    builder.addField("classpath", obj.classpath)
    builder.addField("classDirectory", obj.classDirectory)
    builder.endObject()
  }
}
}
