/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement.ivy

import _root_.sjsonnew.JsonFormat
trait IvyConfigurationFormats { self: sbt.internal.librarymanagement.formats.GlobalLockFormat
    with sbt.internal.librarymanagement.formats.LoggerFormat
    with sbt.librarymanagement.ivy.formats.UpdateOptionsFormat
    with sbt.librarymanagement.ivy.IvyPathsFormats
    with sbt.librarymanagement.ModuleIDFormats
    with sjsonnew.BasicJsonProtocol
    with sbt.librarymanagement.ResolverFormats
    with sbt.librarymanagement.ModuleConfigurationFormats
    with sbt.librarymanagement.ivy.InlineIvyConfigurationFormats
    with sbt.librarymanagement.ivy.ExternalIvyConfigurationFormats
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
    implicit lazy val IvyConfigurationFormat: JsonFormat[sbt.librarymanagement.ivy.IvyConfiguration] = flatUnionFormat2[sbt.librarymanagement.ivy.IvyConfiguration, sbt.librarymanagement.ivy.InlineIvyConfiguration, sbt.librarymanagement.ivy.ExternalIvyConfiguration]("type")
}
