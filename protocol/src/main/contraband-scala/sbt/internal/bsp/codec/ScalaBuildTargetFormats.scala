/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ScalaBuildTargetFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val ScalaBuildTargetFormat: JsonFormat[sbt.internal.bsp.ScalaBuildTarget] = new JsonFormat[sbt.internal.bsp.ScalaBuildTarget] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.ScalaBuildTarget = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val scalaOrganization = unbuilder.readField[String]("scalaOrganization")
      val scalaVersion = unbuilder.readField[String]("scalaVersion")
      val scalaBinaryVersion = unbuilder.readField[String]("scalaBinaryVersion")
      val platform = unbuilder.readField[Int]("platform")
      val jars = unbuilder.readField[Vector[String]]("jars")
      unbuilder.endObject()
      sbt.internal.bsp.ScalaBuildTarget(scalaOrganization, scalaVersion, scalaBinaryVersion, platform, jars)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.ScalaBuildTarget, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("scalaOrganization", obj.scalaOrganization)
    builder.addField("scalaVersion", obj.scalaVersion)
    builder.addField("scalaBinaryVersion", obj.scalaBinaryVersion)
    builder.addField("platform", obj.platform)
    builder.addField("jars", obj.jars)
    builder.endObject()
  }
}
}
