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
import sbt.io.IO

/** Credential-loading utilities that work with sbt.librarymanagement.Credentials without Ivy. */
object CredentialUtils:

  def forHost(sc: Seq[Credentials], host: String): Option[Credentials.DirectCredentials] =
    allDirect(sc).find(_.host == host)

  def allDirect(sc: Seq[Credentials]): Seq[Credentials.DirectCredentials] =
    sc.map(toDirect)

  def toDirect(c: Credentials): Credentials.DirectCredentials = c match
    case dc: Credentials.DirectCredentials => dc
    case fc: Credentials.FileCredentials =>
      loadCredentials(fc.path) match
        case Left(err) => sys.error(err)
        case Right(dc) => dc

  def loadCredentials(path: File): Either[String, Credentials.DirectCredentials] =
    if !path.exists then Left("Credentials file " + path + " does not exist")
    else
      val props = read(path)
      for
        host <- lookup(props, HostKeys, path)
        user <- lookup(props, UserKeys, path)
        pass <- lookup(props, PasswordKeys, path)
      yield
        val realm = props.keysIterator.find(RealmKeys.contains).flatMap(props.get).orNull
        new Credentials.DirectCredentials(realm, host, user, pass)

  private val RealmKeys = Set("realm")
  private val HostKeys = List("host", "hostname")
  private val UserKeys = List("user", "user.name", "username")
  private val PasswordKeys = List("password", "pwd", "pass", "passwd")

  private def lookup(
      props: Map[String, String],
      keys: List[String],
      path: File
  ): Either[String, String] =
    keys
      .flatMap(props.get)
      .headOption
      .toRight(s"${keys.head} not specified in credentials file: $path")

  import scala.jdk.CollectionConverters.*
  private def read(from: File): Map[String, String] =
    val properties = new java.util.Properties
    IO.load(properties, from)
    properties.asScala.map((k, v) => (k, v.trim)).toMap
end CredentialUtils
