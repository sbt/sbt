/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
final class KeyFileAuthentication private (
  val user: String,
  val keyfile: java.io.File,
  val password: Option[String]) extends sbt.librarymanagement.SshAuthentication() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: KeyFileAuthentication => (this.user == x.user) && (this.keyfile == x.keyfile) && (this.password == x.password)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.KeyFileAuthentication".##) + user.##) + keyfile.##) + password.##)
  }
  override def toString: String = {
    "KeyFileAuthentication(" + user + ", " + keyfile + ", " + password + ")"
  }
  private[this] def copy(user: String = user, keyfile: java.io.File = keyfile, password: Option[String] = password): KeyFileAuthentication = {
    new KeyFileAuthentication(user, keyfile, password)
  }
  def withUser(user: String): KeyFileAuthentication = {
    copy(user = user)
  }
  def withKeyfile(keyfile: java.io.File): KeyFileAuthentication = {
    copy(keyfile = keyfile)
  }
  def withPassword(password: Option[String]): KeyFileAuthentication = {
    copy(password = password)
  }
  def withPassword(password: String): KeyFileAuthentication = {
    copy(password = Option(password))
  }
}
object KeyFileAuthentication {
  
  def apply(user: String, keyfile: java.io.File, password: Option[String]): KeyFileAuthentication = new KeyFileAuthentication(user, keyfile, password)
  def apply(user: String, keyfile: java.io.File, password: String): KeyFileAuthentication = new KeyFileAuthentication(user, keyfile, Option(password))
}
