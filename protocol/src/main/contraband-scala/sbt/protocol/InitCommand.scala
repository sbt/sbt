/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class InitCommand private (
  val token: Option[String],
  val execId: Option[String]) extends sbt.protocol.CommandMessage() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: InitCommand => (this.token == x.token) && (this.execId == x.execId)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.protocol.InitCommand".##) + token.##) + execId.##)
  }
  override def toString: String = {
    "InitCommand(" + token + ", " + execId + ")"
  }
  protected[this] def copy(token: Option[String] = token, execId: Option[String] = execId): InitCommand = {
    new InitCommand(token, execId)
  }
  def withToken(token: Option[String]): InitCommand = {
    copy(token = token)
  }
  def withToken(token: String): InitCommand = {
    copy(token = Option(token))
  }
  def withExecId(execId: Option[String]): InitCommand = {
    copy(execId = execId)
  }
  def withExecId(execId: String): InitCommand = {
    copy(execId = Option(execId))
  }
}
object InitCommand {
  
  def apply(token: Option[String], execId: Option[String]): InitCommand = new InitCommand(token, execId)
  def apply(token: String, execId: String): InitCommand = new InitCommand(Option(token), Option(execId))
}
