/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package lmcoursier.definitions
final class Authentication private (
  val user: String,
  val password: String,
  val optional: Boolean,
  val realmOpt: Option[String]) extends Serializable {
  
  private def this(user: String, password: String) = this(user, password, false, None)
  
  override def equals(o: Any): Boolean = o match {
    case x: Authentication => (this.user == x.user) && (this.password == x.password) && (this.optional == x.optional) && (this.realmOpt == x.realmOpt)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "lmcoursier.definitions.Authentication".##) + user.##) + password.##) + optional.##) + realmOpt.##)
  }
  override def toString: String = {
    "Authentication(" + user + ", " + password + ", " + optional + ", " + realmOpt + ")"
  }
  private[this] def copy(user: String = user, password: String = password, optional: Boolean = optional, realmOpt: Option[String] = realmOpt): Authentication = {
    new Authentication(user, password, optional, realmOpt)
  }
  def withUser(user: String): Authentication = {
    copy(user = user)
  }
  def withPassword(password: String): Authentication = {
    copy(password = password)
  }
  def withOptional(optional: Boolean): Authentication = {
    copy(optional = optional)
  }
  def withRealmOpt(realmOpt: Option[String]): Authentication = {
    copy(realmOpt = realmOpt)
  }
}
object Authentication {
  
  def apply(user: String, password: String): Authentication = new Authentication(user, password)
  def apply(user: String, password: String, optional: Boolean, realmOpt: Option[String]): Authentication = new Authentication(user, password, optional, realmOpt)
}
