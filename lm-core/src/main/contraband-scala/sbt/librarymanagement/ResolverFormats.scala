/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement

import _root_.sjsonnew.JsonFormat
trait ResolverFormats { self: sjsonnew.BasicJsonProtocol & sbt.librarymanagement.ChainedResolverFormats & sbt.librarymanagement.MavenRepoFormats & sbt.librarymanagement.MavenCacheFormats & sbt.librarymanagement.PatternsFormats & sbt.librarymanagement.FileConfigurationFormats & sbt.librarymanagement.FileRepositoryFormats & sbt.librarymanagement.URLRepositoryFormats & sbt.librarymanagement.SshConnectionFormats & sbt.librarymanagement.SshAuthenticationFormats & sbt.librarymanagement.SshRepositoryFormats & sbt.librarymanagement.SftpRepositoryFormats =>
implicit lazy val ResolverFormat: JsonFormat[sbt.librarymanagement.Resolver] = flatUnionFormat7[sbt.librarymanagement.Resolver, sbt.librarymanagement.ChainedResolver, sbt.librarymanagement.MavenRepo, sbt.librarymanagement.MavenCache, sbt.librarymanagement.FileRepository, sbt.librarymanagement.URLRepository, sbt.librarymanagement.SshRepository, sbt.librarymanagement.SftpRepository]("type")
}
