/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ScalaTestClassesResultFormats { self: sbt.internal.bsp.codec.ScalaTestClassesItemFormats & sbt.internal.bsp.codec.BuildTargetIdentifierFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val ScalaTestClassesResultFormat: JsonFormat[sbt.internal.bsp.ScalaTestClassesResult] = new JsonFormat[sbt.internal.bsp.ScalaTestClassesResult] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.ScalaTestClassesResult = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val items = unbuilder.readField[Vector[sbt.internal.bsp.ScalaTestClassesItem]]("items")
      val originId = unbuilder.readField[Option[String]]("originId")
      unbuilder.endObject()
      sbt.internal.bsp.ScalaTestClassesResult(items, originId)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.ScalaTestClassesResult, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("items", obj.items)
    builder.addField("originId", obj.originId)
    builder.endObject()
  }
}
}
