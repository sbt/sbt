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
  val canWork: Option[Boolean],
  val subscribeToAll: Option[Boolean],
  val clientName: Option[String],
  val subscribeToExecs: Option[Boolean]) extends Serializable {
  
  private def this(token: Option[String]) = this(token, None, None, None, None, None)
  private def this(token: Option[String], skipAnalysis: Option[Boolean]) = this(token, skipAnalysis, None, None, None, None)
  private def this(token: Option[String], skipAnalysis: Option[Boolean], canWork: Option[Boolean]) = this(token, skipAnalysis, canWork, None, None, None)
  private def this(token: Option[String], skipAnalysis: Option[Boolean], canWork: Option[Boolean], subscribeToAll: Option[Boolean]) = this(token, skipAnalysis, canWork, subscribeToAll, None, None)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: InitializeOption => (this.token == x.token) && (this.skipAnalysis == x.skipAnalysis) && (this.canWork == x.canWork) && (this.subscribeToAll == x.subscribeToAll) && (this.clientName == x.clientName) && (this.subscribeToExecs == x.subscribeToExecs)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.protocol.InitializeOption".##) + token.##) + skipAnalysis.##) + canWork.##) + subscribeToAll.##) + clientName.##) + subscribeToExecs.##)
  }
  override def toString: String = {
    "InitializeOption(" + token + ", " + skipAnalysis + ", " + canWork + ", " + subscribeToAll + ", " + clientName + ", " + subscribeToExecs + ")"
  }
  private def copy(token: Option[String] = token, skipAnalysis: Option[Boolean] = skipAnalysis, canWork: Option[Boolean] = canWork, subscribeToAll: Option[Boolean] = subscribeToAll, clientName: Option[String] = clientName, subscribeToExecs: Option[Boolean] = subscribeToExecs): InitializeOption = {
    new InitializeOption(token, skipAnalysis, canWork, subscribeToAll, clientName, subscribeToExecs)
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
  def withSubscribeToAll(subscribeToAll: Option[Boolean]): InitializeOption = {
    copy(subscribeToAll = subscribeToAll)
  }
  def withSubscribeToAll(subscribeToAll: Boolean): InitializeOption = {
    copy(subscribeToAll = Option(subscribeToAll))
  }
  def withClientName(clientName: Option[String]): InitializeOption = {
    copy(clientName = clientName)
  }
  def withClientName(clientName: String): InitializeOption = {
    copy(clientName = Option(clientName))
  }
  def withSubscribeToExecs(subscribeToExecs: Option[Boolean]): InitializeOption = {
    copy(subscribeToExecs = subscribeToExecs)
  }
  def withSubscribeToExecs(subscribeToExecs: Boolean): InitializeOption = {
    copy(subscribeToExecs = Option(subscribeToExecs))
  }
}
object InitializeOption {
  
  def apply(token: Option[String]): InitializeOption = new InitializeOption(token)
  def apply(token: String): InitializeOption = new InitializeOption(Option(token))
  def apply(token: Option[String], skipAnalysis: Option[Boolean]): InitializeOption = new InitializeOption(token, skipAnalysis)
  def apply(token: String, skipAnalysis: Boolean): InitializeOption = new InitializeOption(Option(token), Option(skipAnalysis))
  def apply(token: Option[String], skipAnalysis: Option[Boolean], canWork: Option[Boolean]): InitializeOption = new InitializeOption(token, skipAnalysis, canWork)
  def apply(token: String, skipAnalysis: Boolean, canWork: Boolean): InitializeOption = new InitializeOption(Option(token), Option(skipAnalysis), Option(canWork))
  def apply(token: Option[String], skipAnalysis: Option[Boolean], canWork: Option[Boolean], subscribeToAll: Option[Boolean]): InitializeOption = new InitializeOption(token, skipAnalysis, canWork, subscribeToAll)
  def apply(token: String, skipAnalysis: Boolean, canWork: Boolean, subscribeToAll: Boolean): InitializeOption = new InitializeOption(Option(token), Option(skipAnalysis), Option(canWork), Option(subscribeToAll))
  def apply(token: Option[String], skipAnalysis: Option[Boolean], canWork: Option[Boolean], subscribeToAll: Option[Boolean], clientName: Option[String], subscribeToExecs: Option[Boolean]): InitializeOption = new InitializeOption(token, skipAnalysis, canWork, subscribeToAll, clientName, subscribeToExecs)
  def apply(token: String, skipAnalysis: Boolean, canWork: Boolean, subscribeToAll: Boolean, clientName: String, subscribeToExecs: Boolean): InitializeOption = new InitializeOption(Option(token), Option(skipAnalysis), Option(canWork), Option(subscribeToAll), Option(clientName), Option(subscribeToExecs))
}
