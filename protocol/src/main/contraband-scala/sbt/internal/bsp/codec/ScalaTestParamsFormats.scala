/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ScalaTestParamsFormats { self: sbt.internal.bsp.codec.ScalaTestClassesItemFormats & sbt.internal.bsp.codec.BuildTargetIdentifierFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val ScalaTestParamsFormat: JsonFormat[sbt.internal.bsp.ScalaTestParams] = new JsonFormat[sbt.internal.bsp.ScalaTestParams] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.ScalaTestParams = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val testClasses = unbuilder.readField[Vector[sbt.internal.bsp.ScalaTestClassesItem]]("testClasses")
      unbuilder.endObject()
      sbt.internal.bsp.ScalaTestParams(testClasses)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.ScalaTestParams, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("testClasses", obj.testClasses)
    builder.endObject()
  }
}
}
