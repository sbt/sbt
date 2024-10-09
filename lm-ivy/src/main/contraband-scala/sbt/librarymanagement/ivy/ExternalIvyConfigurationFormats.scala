/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement.ivy
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ExternalIvyConfigurationFormats { self: sbt.internal.librarymanagement.formats.GlobalLockFormat
    with sbt.internal.librarymanagement.formats.LoggerFormat
    with sbt.librarymanagement.ivy.formats.UpdateOptionsFormat
    with sbt.librarymanagement.ModuleIDFormats
    with sbt.librarymanagement.ResolverFormats
    with sjsonnew.BasicJsonProtocol
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
    implicit lazy val ExternalIvyConfigurationFormat: JsonFormat[sbt.librarymanagement.ivy.ExternalIvyConfiguration] = new JsonFormat[sbt.librarymanagement.ivy.ExternalIvyConfiguration] {
      override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.ivy.ExternalIvyConfiguration = {
        __jsOpt match {
          case Some(__js) =>
          unbuilder.beginObject(__js)
          val lock = unbuilder.readField[Option[xsbti.GlobalLock]]("lock")
          val log = unbuilder.readField[Option[xsbti.Logger]]("log")
          val updateOptions = unbuilder.readField[sbt.librarymanagement.ivy.UpdateOptions]("updateOptions")
          val baseDirectory = unbuilder.readField[Option[java.io.File]]("baseDirectory")
          val uri = unbuilder.readField[Option[java.net.URI]]("uri")
          val extraResolvers = unbuilder.readField[Vector[sbt.librarymanagement.Resolver]]("extraResolvers")
          unbuilder.endObject()
          sbt.librarymanagement.ivy.ExternalIvyConfiguration(lock, log, updateOptions, baseDirectory, uri, extraResolvers)
          case None =>
          deserializationError("Expected JsObject but found None")
        }
      }
      override def write[J](obj: sbt.librarymanagement.ivy.ExternalIvyConfiguration, builder: Builder[J]): Unit = {
        builder.beginObject()
        builder.addField("lock", obj.lock)
        builder.addField("log", obj.log)
        builder.addField("updateOptions", obj.updateOptions)
        builder.addField("baseDirectory", obj.baseDirectory)
        builder.addField("uri", obj.uri)
        builder.addField("extraResolvers", obj.extraResolvers)
        builder.endObject()
      }
    }
}
