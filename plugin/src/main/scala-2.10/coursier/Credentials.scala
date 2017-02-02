package coursier

import java.io.{File, FileInputStream}
import java.util.Properties

import coursier.core.Authentication

sealed abstract class Credentials extends Product with Serializable {
  def user: String
  def password: String

  def authentication: Authentication =
    Authentication(user, password)
}

object Credentials {

  final case class Direct(user: String, password: String) extends Credentials {
    override def toString = s"Direct($user, ******)"
  }

  final case class FromFile(file: File) extends Credentials {

    private lazy val props = {
      val p = new Properties()
      p.load(new FileInputStream(file))
      p
    }

    private def findKey(keys: Seq[String]) = keys
      .iterator
      .map(props.getProperty)
      .filter(_ != null)
      .toStream
      .headOption
      .getOrElse {
        throw new NoSuchElementException(s"${keys.head} key in $file")
      }

    lazy val user: String = findKey(FromFile.fileUserKeys)
    lazy val password: String = findKey(FromFile.filePasswordKeys)
  }

  object FromFile {
    // from sbt.Credentials
    private val fileUserKeys = Seq("user", "user.name", "username")
    private val filePasswordKeys = Seq("password", "pwd", "pass", "passwd")
  }


  def apply(user: String, password: String): Credentials =
    Direct(user, password)

  def apply(file: File): Credentials =
    FromFile(file)

}
