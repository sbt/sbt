/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol
final class InitializeOption private (
  val token: Option[String],
  val skipAnalysis: Option[Boolean]) extends Serializable {
  
  private def this(token: Option[String]) = this(token, None)
  
  override def equals(o: Any): Boolean = o match {
    case x: InitializeOption => (this.token == x.token) && (this.skipAnalysis == x.skipAnalysis)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.protocol.InitializeOption".##) + token.##) + skipAnalysis.##)
  }
  override def toString: String = {
    "InitializeOption(" + token + ", " + skipAnalysis + ")"
  }
  private[this] def copy(token: Option[String] = token, skipAnalysis: Option[Boolean] = skipAnalysis): InitializeOption = {
    new InitializeOption(token, skipAnalysis)
  }
  def withToken(token: Option[String]): InitializeOption = {
    copy(token = token)
  }
  def withToken(token: String): InitializeOption = {
    copy(token = Option(token))
  }
  def withSkipAnalysis(skipAnalysis: Option[Boolean]): InitializeOption = {
    copy(skipAnalysis = skipAnalysis)
  }
  def withSkipAnalysis(skipAnalysis: Boolean): InitializeOption = {
    copy(skipAnalysis = Option(skipAnalysis))
  }
}
object InitializeOption {
  
  def apply(token: Option[String]): InitializeOption = new InitializeOption(token)
  def apply(token: String): InitializeOption = new InitializeOption(Option(token))
  def apply(token: Option[String], skipAnalysis: Option[Boolean]): InitializeOption = new InitializeOption(token, skipAnalysis)
  def apply(token: String, skipAnalysis: Boolean): InitializeOption = new InitializeOption(Option(token), Option(skipAnalysis))
}
