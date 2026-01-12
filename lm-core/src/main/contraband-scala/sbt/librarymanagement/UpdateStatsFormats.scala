/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait UpdateStatsFormats { self: sjsonnew.BasicJsonProtocol =>
given UpdateStatsFormat: JsonFormat[sbt.librarymanagement.UpdateStats] = new JsonFormat[sbt.librarymanagement.UpdateStats] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.UpdateStats = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val resolveTime = unbuilder.readField[Long]("resolveTime")
      val downloadTime = unbuilder.readField[Long]("downloadTime")
      val downloadSize = unbuilder.readField[Long]("downloadSize")
      val cached = unbuilder.readField[Boolean]("cached")
      // Backwards compatibility: old cache files don't have stamp field
      val stamp = try { unbuilder.readField[String]("stamp") }
        catch { case _: Throwable => "" }
      unbuilder.endObject()
      sbt.librarymanagement.UpdateStats(resolveTime, downloadTime, downloadSize, cached, stamp)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.UpdateStats, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("resolveTime", obj.resolveTime)
    builder.addField("downloadTime", obj.downloadTime)
    builder.addField("downloadSize", obj.downloadSize)
    builder.addField("cached", obj.cached)
    builder.addField("stamp", obj.stamp)
    builder.endObject()
  }
}
}
