/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package librarymanagement

import java.io.File

sealed trait Credentials

object Credentials:
  def apply(realm: String, host: String, userName: String, passwd: String): Credentials =
    new DirectCredentials(realm, host, userName, passwd)
  def apply(file: File): Credentials =
    new FileCredentials(file)

  final class FileCredentials(val path: File) extends Credentials {
    override def toString = s"""FileCredentials("$path")"""
  }

  final class DirectCredentials(
      val realm: String,
      val host: String,
      val userName: String,
      val passwd: String
  ) extends Credentials {
    override def toString = {
      val dq = '"'
      val r =
        if (realm == null) "null"
        else s"$dq$realm$dq"
      s"""DirectCredentials($r, "$host", "$userName", ****)"""
    }
  }
end Credentials
