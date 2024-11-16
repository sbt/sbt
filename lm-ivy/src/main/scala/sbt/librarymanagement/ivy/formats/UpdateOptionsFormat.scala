package sbt.librarymanagement.ivy
package formats

import sjsonnew._
import sbt.librarymanagement._

trait UpdateOptionsFormat {
  self: BasicJsonProtocol & ModuleIDFormats & ResolverFormats &
    sbt.librarymanagement.ArtifactFormats & sbt.librarymanagement.ConfigRefFormats &
    sbt.librarymanagement.ChecksumFormats & sbt.librarymanagement.InclExclRuleFormats &
    sbt.librarymanagement.CrossVersionFormats & sbt.librarymanagement.DisabledFormats &
    sbt.librarymanagement.BinaryFormats & sbt.librarymanagement.ConstantFormats &
    sbt.librarymanagement.PatchFormats & sbt.librarymanagement.FullFormats &
    sbt.librarymanagement.For3Use2_13Formats & sbt.librarymanagement.For2_13Use3Formats &
    sbt.librarymanagement.ChainedResolverFormats & sbt.librarymanagement.MavenRepoFormats &
    sbt.librarymanagement.MavenCacheFormats & sbt.librarymanagement.PatternsFormats &
    sbt.librarymanagement.FileConfigurationFormats & sbt.librarymanagement.FileRepositoryFormats &
    sbt.librarymanagement.URLRepositoryFormats & sbt.librarymanagement.SshConnectionFormats &
    sbt.librarymanagement.SshAuthenticationFormats & sbt.librarymanagement.SshRepositoryFormats &
    sbt.librarymanagement.SftpRepositoryFormats &
    sbt.librarymanagement.PasswordAuthenticationFormats &
    sbt.librarymanagement.KeyFileAuthenticationFormats =>
  /* This is necessary to serialize/deserialize `directResolvers`. */
  private given moduleIdJsonKeyFormat: sjsonnew.JsonKeyFormat[ModuleID] = {
    new sjsonnew.JsonKeyFormat[ModuleID] {
      import sjsonnew.support.scalajson.unsafe._
      val moduleIdFormat: JsonFormat[ModuleID] = implicitly[JsonFormat[ModuleID]]
      def write(key: ModuleID): String =
        CompactPrinter(Converter.toJsonUnsafe(key)(moduleIdFormat))
      def read(key: String): ModuleID =
        Converter.fromJsonUnsafe[ModuleID](Parser.parseUnsafe(key))(moduleIdFormat)
    }
  }

  given UpdateOptionsFormat: JsonFormat[UpdateOptions] =
    projectFormat(
      (uo: UpdateOptions) =>
        (
          uo.circularDependencyLevel.name,
          uo.interProjectFirst,
          uo.latestSnapshots,
          uo.cachedResolution,
          uo.gigahorse,
          uo.moduleResolvers
        ),
      (xs: (String, Boolean, Boolean, Boolean, Boolean, Map[ModuleID, Resolver])) =>
        new UpdateOptions(
          levels(xs._1),
          xs._2,
          xs._3,
          xs._4,
          xs._5,
          PartialFunction.empty,
          xs._6
        )
    )

  private val levels: Map[String, CircularDependencyLevel] = Map(
    "warn" -> CircularDependencyLevel.Warn,
    "ignore" -> CircularDependencyLevel.Ignore,
    "error" -> CircularDependencyLevel.Error
  )
}
