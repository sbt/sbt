/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement

import _root_.sjsonnew.JsonFormat
trait SshBasedRepositoryFormats { self: sbt.librarymanagement.PatternsFormats with sbt.librarymanagement.SshConnectionFormats with sjsonnew.BasicJsonProtocol with sbt.librarymanagement.SshRepositoryFormats with sbt.librarymanagement.SftpRepositoryFormats =>
implicit lazy val SshBasedRepositoryFormat: JsonFormat[sbt.librarymanagement.SshBasedRepository] = flatUnionFormat2[sbt.librarymanagement.SshBasedRepository, sbt.librarymanagement.SshRepository, sbt.librarymanagement.SftpRepository]("type")
}
