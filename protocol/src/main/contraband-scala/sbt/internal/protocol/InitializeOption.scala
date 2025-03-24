/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol
/**
 * Passed into InitializeParams as part of "initialize" request as the user-defined option.
 * https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#initialize
 */
final class InitializeOption private (
  val token: Option[String],
  val skipAnalysis: Option[Boolean],
  val canWork: Option[Boolean]) extends Serializable {
  
  private def this(token: Option[String]) = this(token, None, None)
  private def this(token: Option[String], skipAnalysis: Option[Boolean]) = this(token, skipAnalysis, None)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: InitializeOption => (this.token == x.token) && (this.skipAnalysis == x.skipAnalysis) && (this.canWork == x.canWork)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.internal.protocol.InitializeOption".##) + token.##) + skipAnalysis.##) + canWork.##)
  }
  override def toString: String = {
    "InitializeOption(" + token + ", " + skipAnalysis + ", " + canWork + ")"
  }
  private def copy(token: Option[String] = token, skipAnalysis: Option[Boolean] = skipAnalysis, canWork: Option[Boolean] = canWork): InitializeOption = {
    new InitializeOption(token, skipAnalysis, canWork)
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
  def withCanWork(canWork: Option[Boolean]): InitializeOption = {
    copy(canWork = canWork)
  }
  def withCanWork(canWork: Boolean): InitializeOption = {
    copy(canWork = Option(canWork))
  }
}
object InitializeOption {
  
  def apply(token: Option[String]): InitializeOption = new InitializeOption(token)
  def apply(token: String): InitializeOption = new InitializeOption(Option(token))
  def apply(token: Option[String], skipAnalysis: Option[Boolean]): InitializeOption = new InitializeOption(token, skipAnalysis)
  def apply(token: String, skipAnalysis: Boolean): InitializeOption = new InitializeOption(Option(token), Option(skipAnalysis))
  def apply(token: Option[String], skipAnalysis: Option[Boolean], canWork: Option[Boolean]): InitializeOption = new InitializeOption(token, skipAnalysis, canWork)
  def apply(token: String, skipAnalysis: Boolean, canWork: Boolean): InitializeOption = new InitializeOption(Option(token), Option(skipAnalysis), Option(canWork))
}
