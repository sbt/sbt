/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol
final class InitializeOption private (
  val token: Option[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: InitializeOption => (this.token == x.token)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.protocol.InitializeOption".##) + token.##)
  }
  override def toString: String = {
    "InitializeOption(" + token + ")"
  }
  protected[this] def copy(token: Option[String] = token): InitializeOption = {
    new InitializeOption(token)
  }
  def withToken(token: Option[String]): InitializeOption = {
    copy(token = token)
  }
  def withToken(token: String): InitializeOption = {
    copy(token = Option(token))
  }
}
object InitializeOption {
  
  def apply(token: Option[String]): InitializeOption = new InitializeOption(token)
  def apply(token: String): InitializeOption = new InitializeOption(Option(token))
}
