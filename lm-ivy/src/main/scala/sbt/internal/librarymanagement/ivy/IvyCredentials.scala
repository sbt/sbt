/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package sbt.internal.librarymanagement
package ivy

import java.io.File
import org.apache.ivy.util.url.CredentialsStore
import sbt.internal.librarymanagement.IvyUtil
import sbt.io.IO
import sbt.librarymanagement.Credentials
import sbt.util.Logger

object IvyCredentials {

  /** Add the provided credentials to Ivy's credentials cache. */
  def add(realm: String, host: String, userName: String, passwd: String): Unit =
    CredentialsStore.INSTANCE.addCredentials(realm, host, userName, passwd)

  /** Load credentials from the given file into Ivy's credentials cache. */
  def add(path: File, log: Logger): Unit =
    loadCredentials(path) match {
      case Left(err) => log.warn(err)
      case Right(dc) => add(dc.realm, dc.host, dc.userName, dc.passwd)
    }

  def forHost(sc: Seq[Credentials], host: String) = allDirect(sc) find { _.host == host }
  def allDirect(sc: Seq[Credentials]): Seq[Credentials.DirectCredentials] = sc map toDirect
  def toDirect(c: Credentials): Credentials.DirectCredentials = c match {
    case dc: Credentials.DirectCredentials => dc
    case fc: Credentials.FileCredentials   =>
      loadCredentials(fc.path) match {
        case Left(err) => sys.error(err)
        case Right(dc) => dc
      }
  }

  def loadCredentials(path: File): Either[String, Credentials.DirectCredentials] =
    if (path.exists) {
      val properties = read(path)
      def get(keys: List[String]): Either[String, String] =
        keys
          .flatMap(properties.get)
          .headOption
          .toRight(keys.head + " not specified in credentials file: " + path)

      IvyUtil.separate(List(HostKeys, UserKeys, PasswordKeys).map(get)) match
        case (Nil, List(host: String, user: String, pass: String)) =>
          IvyUtil.separate(List(RealmKeys).map(get)) match
            case (_, List(realm: String)) =>
              Right(new Credentials.DirectCredentials(realm, host, user, pass))
            case _ => Right(new Credentials.DirectCredentials(null, host, user, pass))

        case (errors, _) => Left(errors.mkString("\n"))
    } else Left("Credentials file " + path + " does not exist")

  def register(cs: Seq[Credentials], log: Logger): Unit =
    cs foreach {
      case f: Credentials.FileCredentials   => add(f.path, log)
      case d: Credentials.DirectCredentials => add(d.realm, d.host, d.userName, d.passwd)
    }

  private val RealmKeys = List("realm")
  private val HostKeys = List("host", "hostname")
  private val UserKeys = List("user", "user.name", "username")
  private val PasswordKeys = List("password", "pwd", "pass", "passwd")

  import scala.jdk.CollectionConverters.*
  private def read(from: File): Map[String, String] = {
    val properties = new java.util.Properties
    IO.load(properties, from)
    properties.asScala.map { (k, v) => (k, v.trim) }.toMap
  }
}
