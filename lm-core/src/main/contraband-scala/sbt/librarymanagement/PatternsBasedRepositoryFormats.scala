/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement

import _root_.sjsonnew.JsonFormat
trait PatternsBasedRepositoryFormats { self: sbt.librarymanagement.PatternsFormats with sjsonnew.BasicJsonProtocol with sbt.librarymanagement.FileConfigurationFormats with sbt.librarymanagement.FileRepositoryFormats with sbt.librarymanagement.URLRepositoryFormats with sbt.librarymanagement.SshConnectionFormats with sbt.librarymanagement.SshAuthenticationFormats with sbt.librarymanagement.SshRepositoryFormats with sbt.librarymanagement.SftpRepositoryFormats =>
implicit lazy val PatternsBasedRepositoryFormat: JsonFormat[sbt.librarymanagement.PatternsBasedRepository] = flatUnionFormat4[sbt.librarymanagement.PatternsBasedRepository, sbt.librarymanagement.FileRepository, sbt.librarymanagement.URLRepository, sbt.librarymanagement.SshRepository, sbt.librarymanagement.SftpRepository]("type")
}
