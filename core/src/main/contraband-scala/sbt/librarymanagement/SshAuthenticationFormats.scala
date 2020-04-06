/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement

import _root_.sjsonnew.JsonFormat
trait SshAuthenticationFormats { self: sjsonnew.BasicJsonProtocol with sbt.librarymanagement.PasswordAuthenticationFormats with sbt.librarymanagement.KeyFileAuthenticationFormats =>
implicit lazy val SshAuthenticationFormat: JsonFormat[sbt.librarymanagement.SshAuthentication] = flatUnionFormat2[sbt.librarymanagement.SshAuthentication, sbt.librarymanagement.PasswordAuthentication, sbt.librarymanagement.KeyFileAuthentication]("type")
}
