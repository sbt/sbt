/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
final class PasswordAuthentication private (
  val user: String,
  val password: Option[String]) extends sbt.librarymanagement.SshAuthentication() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: PasswordAuthentication => (this.user == x.user) && (this.password == x.password)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.librarymanagement.PasswordAuthentication".##) + user.##) + password.##)
  }
  override def toString: String = {
    "PasswordAuthentication(" + user + ", " + password + ")"
  }
  private[this] def copy(user: String = user, password: Option[String] = password): PasswordAuthentication = {
    new PasswordAuthentication(user, password)
  }
  def withUser(user: String): PasswordAuthentication = {
    copy(user = user)
  }
  def withPassword(password: Option[String]): PasswordAuthentication = {
    copy(password = password)
  }
  def withPassword(password: String): PasswordAuthentication = {
    copy(password = Option(password))
  }
}
object PasswordAuthentication {
  
  def apply(user: String, password: Option[String]): PasswordAuthentication = new PasswordAuthentication(user, password)
  def apply(user: String, password: String): PasswordAuthentication = new PasswordAuthentication(user, Option(password))
}
