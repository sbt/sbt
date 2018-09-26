/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
final class CompletionContext private (
  val triggerKind: Int,
  val triggerCharacter: Option[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: CompletionContext => (this.triggerKind == x.triggerKind) && (this.triggerCharacter == x.triggerCharacter)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.langserver.CompletionContext".##) + triggerKind.##) + triggerCharacter.##)
  }
  override def toString: String = {
    "CompletionContext(" + triggerKind + ", " + triggerCharacter + ")"
  }
  private[this] def copy(triggerKind: Int = triggerKind, triggerCharacter: Option[String] = triggerCharacter): CompletionContext = {
    new CompletionContext(triggerKind, triggerCharacter)
  }
  def withTriggerKind(triggerKind: Int): CompletionContext = {
    copy(triggerKind = triggerKind)
  }
  def withTriggerCharacter(triggerCharacter: Option[String]): CompletionContext = {
    copy(triggerCharacter = triggerCharacter)
  }
  def withTriggerCharacter(triggerCharacter: String): CompletionContext = {
    copy(triggerCharacter = Option(triggerCharacter))
  }
}
object CompletionContext {
  
  def apply(triggerKind: Int, triggerCharacter: Option[String]): CompletionContext = new CompletionContext(triggerKind, triggerCharacter)
  def apply(triggerKind: Int, triggerCharacter: String): CompletionContext = new CompletionContext(triggerKind, Option(triggerCharacter))
}
