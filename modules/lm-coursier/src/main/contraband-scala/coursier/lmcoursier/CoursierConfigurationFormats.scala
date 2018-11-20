/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package coursier.lmcoursier
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait CoursierConfigurationFormats { self: sbt.internal.librarymanagement.formats.LoggerFormat with sbt.librarymanagement.ResolverFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val CoursierConfigurationFormat: JsonFormat[coursier.lmcoursier.CoursierConfiguration] = new JsonFormat[coursier.lmcoursier.CoursierConfiguration] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): coursier.lmcoursier.CoursierConfiguration = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val log = unbuilder.readField[Option[xsbti.Logger]]("log")
      val resolvers = unbuilder.readField[Vector[sbt.librarymanagement.Resolver]]("resolvers")
      val otherResolvers = unbuilder.readField[Vector[sbt.librarymanagement.Resolver]]("otherResolvers")
      val reorderResolvers = unbuilder.readField[Boolean]("reorderResolvers")
      val parallelDownloads = unbuilder.readField[Int]("parallelDownloads")
      val maxIterations = unbuilder.readField[Int]("maxIterations")
      val sbtScalaOrganization = unbuilder.readField[Option[String]]("sbtScalaOrganization")
      val sbtScalaVersion = unbuilder.readField[Option[String]]("sbtScalaVersion")
      val sbtScalaJars = unbuilder.readField[Vector[java.io.File]]("sbtScalaJars")
      unbuilder.endObject()
      coursier.lmcoursier.CoursierConfiguration(log, resolvers, otherResolvers, reorderResolvers, parallelDownloads, maxIterations, sbtScalaOrganization, sbtScalaVersion, sbtScalaJars)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: coursier.lmcoursier.CoursierConfiguration, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("log", obj.log)
    builder.addField("resolvers", obj.resolvers)
    builder.addField("otherResolvers", obj.otherResolvers)
    builder.addField("reorderResolvers", obj.reorderResolvers)
    builder.addField("parallelDownloads", obj.parallelDownloads)
    builder.addField("maxIterations", obj.maxIterations)
    builder.addField("sbtScalaOrganization", obj.sbtScalaOrganization)
    builder.addField("sbtScalaVersion", obj.sbtScalaVersion)
    builder.addField("sbtScalaJars", obj.sbtScalaJars)
    builder.endObject()
  }
}
}
