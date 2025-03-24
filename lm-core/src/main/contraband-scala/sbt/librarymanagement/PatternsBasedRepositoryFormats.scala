/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement

import _root_.sjsonnew.JsonFormat
trait PatternsBasedRepositoryFormats { self: sbt.librarymanagement.PatternsFormats & sjsonnew.BasicJsonProtocol & sbt.librarymanagement.FileConfigurationFormats & sbt.librarymanagement.FileRepositoryFormats & sbt.librarymanagement.URLRepositoryFormats & sbt.librarymanagement.SshConnectionFormats & sbt.librarymanagement.SshAuthenticationFormats & sbt.librarymanagement.SshRepositoryFormats & sbt.librarymanagement.SftpRepositoryFormats =>
implicit lazy val PatternsBasedRepositoryFormat: JsonFormat[sbt.librarymanagement.PatternsBasedRepository] = flatUnionFormat4[sbt.librarymanagement.PatternsBasedRepository, sbt.librarymanagement.FileRepository, sbt.librarymanagement.URLRepository, sbt.librarymanagement.SshRepository, sbt.librarymanagement.SftpRepository]("type")
}
