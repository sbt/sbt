/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement

import _root_.sjsonnew.JsonFormat
trait SshBasedRepositoryFormats { self: sbt.librarymanagement.PatternsFormats & sjsonnew.BasicJsonProtocol & sbt.librarymanagement.SshConnectionFormats & sbt.librarymanagement.SshAuthenticationFormats & sbt.librarymanagement.SshRepositoryFormats & sbt.librarymanagement.SftpRepositoryFormats =>
implicit lazy val SshBasedRepositoryFormat: JsonFormat[sbt.librarymanagement.SshBasedRepository] = flatUnionFormat2[sbt.librarymanagement.SshBasedRepository, sbt.librarymanagement.SshRepository, sbt.librarymanagement.SftpRepository]("type")
}
