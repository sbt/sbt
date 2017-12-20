/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
final class InitializeParams private (
  val processId: Option[Long],
  /** The rootPath of the workspace. */
  val rootPath: Option[String],
  val rootUri: Option[String],
  val initializationOptions: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue],
  val capabilities: Option[sbt.internal.langserver.ClientCapabilities],
  val trace: Option[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: InitializeParams => (this.processId == x.processId) && (this.rootPath == x.rootPath) && (this.rootUri == x.rootUri) && (this.initializationOptions == x.initializationOptions) && (this.capabilities == x.capabilities) && (this.trace == x.trace)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.langserver.InitializeParams".##) + processId.##) + rootPath.##) + rootUri.##) + initializationOptions.##) + capabilities.##) + trace.##)
  }
  override def toString: String = {
    "InitializeParams(" + processId + ", " + rootPath + ", " + rootUri + ", " + initializationOptions + ", " + capabilities + ", " + trace + ")"
  }
  protected[this] def copy(processId: Option[Long] = processId, rootPath: Option[String] = rootPath, rootUri: Option[String] = rootUri, initializationOptions: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue] = initializationOptions, capabilities: Option[sbt.internal.langserver.ClientCapabilities] = capabilities, trace: Option[String] = trace): InitializeParams = {
    new InitializeParams(processId, rootPath, rootUri, initializationOptions, capabilities, trace)
  }
  def withProcessId(processId: Option[Long]): InitializeParams = {
    copy(processId = processId)
  }
  def withProcessId(processId: Long): InitializeParams = {
    copy(processId = Option(processId))
  }
  def withRootPath(rootPath: Option[String]): InitializeParams = {
    copy(rootPath = rootPath)
  }
  def withRootPath(rootPath: String): InitializeParams = {
    copy(rootPath = Option(rootPath))
  }
  def withRootUri(rootUri: Option[String]): InitializeParams = {
    copy(rootUri = rootUri)
  }
  def withRootUri(rootUri: String): InitializeParams = {
    copy(rootUri = Option(rootUri))
  }
  def withInitializationOptions(initializationOptions: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]): InitializeParams = {
    copy(initializationOptions = initializationOptions)
  }
  def withInitializationOptions(initializationOptions: sjsonnew.shaded.scalajson.ast.unsafe.JValue): InitializeParams = {
    copy(initializationOptions = Option(initializationOptions))
  }
  def withCapabilities(capabilities: Option[sbt.internal.langserver.ClientCapabilities]): InitializeParams = {
    copy(capabilities = capabilities)
  }
  def withCapabilities(capabilities: sbt.internal.langserver.ClientCapabilities): InitializeParams = {
    copy(capabilities = Option(capabilities))
  }
  def withTrace(trace: Option[String]): InitializeParams = {
    copy(trace = trace)
  }
  def withTrace(trace: String): InitializeParams = {
    copy(trace = Option(trace))
  }
}
object InitializeParams {
  
  def apply(processId: Option[Long], rootPath: Option[String], rootUri: Option[String], initializationOptions: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue], capabilities: Option[sbt.internal.langserver.ClientCapabilities], trace: Option[String]): InitializeParams = new InitializeParams(processId, rootPath, rootUri, initializationOptions, capabilities, trace)
  def apply(processId: Long, rootPath: String, rootUri: String, initializationOptions: sjsonnew.shaded.scalajson.ast.unsafe.JValue, capabilities: sbt.internal.langserver.ClientCapabilities, trace: String): InitializeParams = new InitializeParams(Option(processId), Option(rootPath), Option(rootUri), Option(initializationOptions), Option(capabilities), Option(trace))
}
