/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class CompletionParams private (
  val query: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: CompletionParams => (this.query == x.query)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.protocol.CompletionParams".##) + query.##)
  }
  override def toString: String = {
    "CompletionParams(" + query + ")"
  }
  private[this] def copy(query: String = query): CompletionParams = {
    new CompletionParams(query)
  }
  def withQuery(query: String): CompletionParams = {
    copy(query = query)
  }
}
object CompletionParams {
  
  def apply(query: String): CompletionParams = new CompletionParams(query)
}
