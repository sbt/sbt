/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
/** @param capabilities The capabilities the language server provides. */
final class InitializeResult private (
  val capabilities: sbt.internal.langserver.ServerCapabilities) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: InitializeResult => (this.capabilities == x.capabilities)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.langserver.InitializeResult".##) + capabilities.##)
  }
  override def toString: String = {
    "InitializeResult(" + capabilities + ")"
  }
  private[this] def copy(capabilities: sbt.internal.langserver.ServerCapabilities = capabilities): InitializeResult = {
    new InitializeResult(capabilities)
  }
  def withCapabilities(capabilities: sbt.internal.langserver.ServerCapabilities): InitializeResult = {
    copy(capabilities = capabilities)
  }
}
object InitializeResult {
  
  def apply(capabilities: sbt.internal.langserver.ServerCapabilities): InitializeResult = new InitializeResult(capabilities)
}
