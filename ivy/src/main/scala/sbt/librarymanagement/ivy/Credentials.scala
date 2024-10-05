/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package sbt.librarymanagement
package ivy

import java.io.File
import org.apache.ivy.util.url.CredentialsStore
import sbt.io.IO
import sbt.util.Logger
import sbt.internal.librarymanagement.IvyUtil

object Credentials {
  def apply(realm: String, host: String, userName: String, passwd: String): Credentials =
    new DirectCredentials(realm, host, userName, passwd)
  def apply(file: File): Credentials =
    new FileCredentials(file)

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
  def allDirect(sc: Seq[Credentials]): Seq[DirectCredentials] = sc map toDirect
  def toDirect(c: Credentials): DirectCredentials = c match {
    case dc: DirectCredentials => dc
    case fc: FileCredentials =>
      loadCredentials(fc.path) match {
        case Left(err) => sys.error(err)
        case Right(dc) => dc
      }
  }

  def loadCredentials(path: File): Either[String, DirectCredentials] =
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
            case (_, List(realm: String)) => Right(new DirectCredentials(realm, host, user, pass))
            case _                        => Right(new DirectCredentials(null, host, user, pass))

        case (errors, _) => Left(errors.mkString("\n"))
    } else Left("Credentials file " + path + " does not exist")

  def register(cs: Seq[Credentials], log: Logger): Unit =
    cs foreach {
      case f: FileCredentials   => add(f.path, log)
      case d: DirectCredentials => add(d.realm, d.host, d.userName, d.passwd)
    }

  private[this] val RealmKeys = List("realm")
  private[this] val HostKeys = List("host", "hostname")
  private[this] val UserKeys = List("user", "user.name", "username")
  private[this] val PasswordKeys = List("password", "pwd", "pass", "passwd")

  import scala.jdk.CollectionConverters._
  private[this] def read(from: File): Map[String, String] = {
    val properties = new java.util.Properties
    IO.load(properties, from)
    properties.asScala.map { case (k, v) => (k.toString, v.toString.trim) }.toMap
  }
}

sealed trait Credentials
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
