/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class InitCommand private (
  val token: Option[String],
  val execId: Option[String],
  val skipAnalysis: Option[Boolean],
  val initializationOptions: Option[sbt.internal.protocol.InitializeOption]) extends sbt.protocol.CommandMessage() with Serializable {
  
  private def this(token: Option[String], execId: Option[String]) = this(token, execId, None, None)
  private def this(token: Option[String], execId: Option[String], skipAnalysis: Option[Boolean]) = this(token, execId, skipAnalysis, None)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: InitCommand => (this.token == x.token) && (this.execId == x.execId) && (this.skipAnalysis == x.skipAnalysis) && (this.initializationOptions == x.initializationOptions)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "sbt.protocol.InitCommand".##) + token.##) + execId.##) + skipAnalysis.##) + initializationOptions.##)
  }
  override def toString: String = {
    "InitCommand(" + token + ", " + execId + ", " + skipAnalysis + ", " + initializationOptions + ")"
  }
  private def copy(token: Option[String] = token, execId: Option[String] = execId, skipAnalysis: Option[Boolean] = skipAnalysis, initializationOptions: Option[sbt.internal.protocol.InitializeOption] = initializationOptions): InitCommand = {
    new InitCommand(token, execId, skipAnalysis, initializationOptions)
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
  def withSkipAnalysis(skipAnalysis: Option[Boolean]): InitCommand = {
    copy(skipAnalysis = skipAnalysis)
  }
  def withSkipAnalysis(skipAnalysis: Boolean): InitCommand = {
    copy(skipAnalysis = Option(skipAnalysis))
  }
  def withInitializationOptions(initializationOptions: Option[sbt.internal.protocol.InitializeOption]): InitCommand = {
    copy(initializationOptions = initializationOptions)
  }
  def withInitializationOptions(initializationOptions: sbt.internal.protocol.InitializeOption): InitCommand = {
    copy(initializationOptions = Option(initializationOptions))
  }
}
object InitCommand {
  
  def apply(token: Option[String], execId: Option[String]): InitCommand = new InitCommand(token, execId)
  def apply(token: String, execId: String): InitCommand = new InitCommand(Option(token), Option(execId))
  def apply(token: Option[String], execId: Option[String], skipAnalysis: Option[Boolean]): InitCommand = new InitCommand(token, execId, skipAnalysis)
  def apply(token: String, execId: String, skipAnalysis: Boolean): InitCommand = new InitCommand(Option(token), Option(execId), Option(skipAnalysis))
  def apply(token: Option[String], execId: Option[String], skipAnalysis: Option[Boolean], initializationOptions: Option[sbt.internal.protocol.InitializeOption]): InitCommand = new InitCommand(token, execId, skipAnalysis, initializationOptions)
  def apply(token: String, execId: String, skipAnalysis: Boolean, initializationOptions: sbt.internal.protocol.InitializeOption): InitCommand = new InitCommand(Option(token), Option(execId), Option(skipAnalysis), Option(initializationOptions))
}
