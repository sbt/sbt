/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ScalaTestClassesItemFormats { self: sbt.internal.bsp.codec.BuildTargetIdentifierFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val ScalaTestClassesItemFormat: JsonFormat[sbt.internal.bsp.ScalaTestClassesItem] = new JsonFormat[sbt.internal.bsp.ScalaTestClassesItem] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.ScalaTestClassesItem = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val target = unbuilder.readField[sbt.internal.bsp.BuildTargetIdentifier]("target")
      val classes = unbuilder.readField[Vector[String]]("classes")
      val framework = unbuilder.readField[Option[String]]("framework")
      unbuilder.endObject()
      sbt.internal.bsp.ScalaTestClassesItem(target, classes, framework)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.ScalaTestClassesItem, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("target", obj.target)
    builder.addField("classes", obj.classes)
    builder.addField("framework", obj.framework)
    builder.endObject()
  }
}
}
