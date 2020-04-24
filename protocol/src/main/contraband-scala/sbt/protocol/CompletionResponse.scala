/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class CompletionResponse private (
  val items: Vector[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: CompletionResponse => (this.items == x.items)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.protocol.CompletionResponse".##) + items.##)
  }
  override def toString: String = {
    "CompletionResponse(" + items + ")"
  }
  private[this] def copy(items: Vector[String] = items): CompletionResponse = {
    new CompletionResponse(items)
  }
  def withItems(items: Vector[String]): CompletionResponse = {
    copy(items = items)
  }
}
object CompletionResponse {
  
  def apply(items: Vector[String]): CompletionResponse = new CompletionResponse(items)
}
