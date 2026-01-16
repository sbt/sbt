/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.librarymanagement.ivy
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait InlineIvyConfigurationFormats { self: sbt.internal.librarymanagement.formats.GlobalLockFormat & sbt.internal.librarymanagement.formats.LoggerFormat & sbt.internal.librarymanagement.ivy.formats.UpdateOptionsFormat & sbt.librarymanagement.IvyPathsFormats & sbt.librarymanagement.ResolverFormats & sbt.librarymanagement.ModuleConfigurationFormats & sjsonnew.BasicJsonProtocol =>
given InlineIvyConfigurationFormat: JsonFormat[sbt.internal.librarymanagement.ivy.InlineIvyConfiguration] = new JsonFormat[sbt.internal.librarymanagement.ivy.InlineIvyConfiguration] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.librarymanagement.ivy.InlineIvyConfiguration = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val lock = unbuilder.readField[Option[xsbti.GlobalLock]]("lock")
      val log = unbuilder.readField[Option[xsbti.Logger]]("log")
      val updateOptions = unbuilder.readField[sbt.internal.librarymanagement.ivy.UpdateOptions]("updateOptions")
      val paths = unbuilder.readField[Option[sbt.librarymanagement.IvyPaths]]("paths")
      val resolvers = unbuilder.readField[Vector[sbt.librarymanagement.Resolver]]("resolvers")
      val otherResolvers = unbuilder.readField[Vector[sbt.librarymanagement.Resolver]]("otherResolvers")
      val moduleConfigurations = unbuilder.readField[Vector[sbt.librarymanagement.ModuleConfiguration]]("moduleConfigurations")
      val checksums = unbuilder.readField[Vector[String]]("checksums")
      val managedChecksums = unbuilder.readField[Boolean]("managedChecksums")
      val resolutionCacheDir = unbuilder.readField[Option[java.io.File]]("resolutionCacheDir")
      unbuilder.endObject()
      sbt.internal.librarymanagement.ivy.InlineIvyConfiguration(lock, log, updateOptions, paths, resolvers, otherResolvers, moduleConfigurations, checksums, managedChecksums, resolutionCacheDir)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.librarymanagement.ivy.InlineIvyConfiguration, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("lock", obj.lock)
    builder.addField("log", obj.log)
    builder.addField("updateOptions", obj.updateOptions)
    builder.addField("paths", obj.paths)
    builder.addField("resolvers", obj.resolvers)
    builder.addField("otherResolvers", obj.otherResolvers)
    builder.addField("moduleConfigurations", obj.moduleConfigurations)
    builder.addField("checksums", obj.checksums)
    builder.addField("managedChecksums", obj.managedChecksums)
    builder.addField("resolutionCacheDir", obj.resolutionCacheDir)
    builder.endObject()
  }
}
}
