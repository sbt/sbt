/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement

import _root_.sjsonnew.JsonFormat
trait ResolverFormats { self: sjsonnew.BasicJsonProtocol with sbt.librarymanagement.ChainedResolverFormats with sbt.librarymanagement.MavenRepoFormats with sbt.librarymanagement.MavenCacheFormats with sbt.librarymanagement.PatternsFormats with sbt.librarymanagement.FileConfigurationFormats with sbt.librarymanagement.FileRepositoryFormats with sbt.librarymanagement.URLRepositoryFormats with sbt.librarymanagement.SshConnectionFormats with sbt.librarymanagement.SshAuthenticationFormats with sbt.librarymanagement.SshRepositoryFormats with sbt.librarymanagement.SftpRepositoryFormats =>
implicit lazy val ResolverFormat: JsonFormat[sbt.librarymanagement.Resolver] = flatUnionFormat7[sbt.librarymanagement.Resolver, sbt.librarymanagement.ChainedResolver, sbt.librarymanagement.MavenRepo, sbt.librarymanagement.MavenCache, sbt.librarymanagement.FileRepository, sbt.librarymanagement.URLRepository, sbt.librarymanagement.SshRepository, sbt.librarymanagement.SftpRepository]("type")
}
