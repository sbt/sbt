/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
/** Completion request interfaces */
final class CompletionParams private (
  val context: Option[sbt.internal.langserver.CompletionContext]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: CompletionParams => (this.context == x.context)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.langserver.CompletionParams".##) + context.##)
  }
  override def toString: String = {
    "CompletionParams(" + context + ")"
  }
  private[this] def copy(context: Option[sbt.internal.langserver.CompletionContext] = context): CompletionParams = {
    new CompletionParams(context)
  }
  def withContext(context: Option[sbt.internal.langserver.CompletionContext]): CompletionParams = {
    copy(context = context)
  }
  def withContext(context: sbt.internal.langserver.CompletionContext): CompletionParams = {
    copy(context = Option(context))
  }
}
object CompletionParams {
  
  def apply(context: Option[sbt.internal.langserver.CompletionContext]): CompletionParams = new CompletionParams(context)
  def apply(context: sbt.internal.langserver.CompletionContext): CompletionParams = new CompletionParams(Option(context))
}
