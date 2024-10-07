/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement.ivy
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait InlineIvyConfigurationFormats { self: sbt.internal.librarymanagement.formats.GlobalLockFormat
    with sbt.internal.librarymanagement.formats.LoggerFormat
    with sbt.librarymanagement.ivy.formats.UpdateOptionsFormat
    with sbt.librarymanagement.ivy.IvyPathsFormats
    with sjsonnew.BasicJsonProtocol
    with sbt.librarymanagement.ModuleIDFormats
    with sbt.librarymanagement.ResolverFormats
    with sbt.librarymanagement.ModuleConfigurationFormats
    with sbt.librarymanagement.ArtifactFormats
    with sbt.librarymanagement.ConfigRefFormats
    with sbt.librarymanagement.ChecksumFormats
    with sbt.librarymanagement.InclExclRuleFormats
    with sbt.librarymanagement.CrossVersionFormats
    with sbt.librarymanagement.DisabledFormats
    with sbt.librarymanagement.BinaryFormats
    with sbt.librarymanagement.ConstantFormats
    with sbt.librarymanagement.PatchFormats
    with sbt.librarymanagement.FullFormats
    with sbt.librarymanagement.For3Use2_13Formats
    with sbt.librarymanagement.For2_13Use3Formats
    with sbt.librarymanagement.ChainedResolverFormats
    with sbt.librarymanagement.MavenRepoFormats
    with sbt.librarymanagement.MavenCacheFormats
    with sbt.librarymanagement.PatternsFormats
    with sbt.librarymanagement.FileConfigurationFormats
    with sbt.librarymanagement.FileRepositoryFormats
    with sbt.librarymanagement.URLRepositoryFormats
    with sbt.librarymanagement.SshConnectionFormats
    with sbt.librarymanagement.SshAuthenticationFormats
    with sbt.librarymanagement.SshRepositoryFormats
    with sbt.librarymanagement.SftpRepositoryFormats
    with sbt.librarymanagement.PasswordAuthenticationFormats
    with sbt.librarymanagement.KeyFileAuthenticationFormats =>
    implicit lazy val InlineIvyConfigurationFormat: JsonFormat[sbt.librarymanagement.ivy.InlineIvyConfiguration] = new JsonFormat[sbt.librarymanagement.ivy.InlineIvyConfiguration] {
      override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.ivy.InlineIvyConfiguration = {
        __jsOpt match {
          case Some(__js) =>
          unbuilder.beginObject(__js)
          val lock = unbuilder.readField[Option[xsbti.GlobalLock]]("lock")
          val log = unbuilder.readField[Option[xsbti.Logger]]("log")
          val updateOptions = unbuilder.readField[sbt.librarymanagement.ivy.UpdateOptions]("updateOptions")
          val paths = unbuilder.readField[Option[sbt.librarymanagement.ivy.IvyPaths]]("paths")
          val resolvers = unbuilder.readField[Vector[sbt.librarymanagement.Resolver]]("resolvers")
          val otherResolvers = unbuilder.readField[Vector[sbt.librarymanagement.Resolver]]("otherResolvers")
          val moduleConfigurations = unbuilder.readField[Vector[sbt.librarymanagement.ModuleConfiguration]]("moduleConfigurations")
          val checksums = unbuilder.readField[Vector[String]]("checksums")
          val managedChecksums = unbuilder.readField[Boolean]("managedChecksums")
          val resolutionCacheDir = unbuilder.readField[Option[java.io.File]]("resolutionCacheDir")
          unbuilder.endObject()
          sbt.librarymanagement.ivy.InlineIvyConfiguration(lock, log, updateOptions, paths, resolvers, otherResolvers, moduleConfigurations, checksums, managedChecksums, resolutionCacheDir)
          case None =>
          deserializationError("Expected JsObject but found None")
        }
      }
      override def write[J](obj: sbt.librarymanagement.ivy.InlineIvyConfiguration, builder: Builder[J]): Unit = {
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
