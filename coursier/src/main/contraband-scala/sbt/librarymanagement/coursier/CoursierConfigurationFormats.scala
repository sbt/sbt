/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement.coursier
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait CoursierConfigurationFormats { self: sbt.internal.librarymanagement.formats.LoggerFormat with sbt.librarymanagement.ResolverFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val CoursierConfigurationFormat: JsonFormat[sbt.librarymanagement.coursier.CoursierConfiguration] = new JsonFormat[sbt.librarymanagement.coursier.CoursierConfiguration] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.coursier.CoursierConfiguration = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val log = unbuilder.readField[Option[xsbti.Logger]]("log")
      val resolvers = unbuilder.readField[Vector[sbt.librarymanagement.Resolver]]("resolvers")
      val otherResolvers = unbuilder.readField[Vector[sbt.librarymanagement.Resolver]]("otherResolvers")
      val reorderResolvers = unbuilder.readField[Boolean]("reorderResolvers")
      val parallelDownloads = unbuilder.readField[Int]("parallelDownloads")
      val maxIterations = unbuilder.readField[Int]("maxIterations")
      unbuilder.endObject()
      sbt.librarymanagement.coursier.CoursierConfiguration(log, resolvers, otherResolvers, reorderResolvers, parallelDownloads, maxIterations)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.coursier.CoursierConfiguration, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("log", obj.log)
    builder.addField("resolvers", obj.resolvers)
    builder.addField("otherResolvers", obj.otherResolvers)
    builder.addField("reorderResolvers", obj.reorderResolvers)
    builder.addField("parallelDownloads", obj.parallelDownloads)
    builder.addField("maxIterations", obj.maxIterations)
    builder.endObject()
  }
}
}
