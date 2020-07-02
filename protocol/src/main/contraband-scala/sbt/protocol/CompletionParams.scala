/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class CompletionParams private (
  val query: String,
  val level: Option[Int]) extends Serializable {
  
  private def this(query: String) = this(query, None)
  
  override def equals(o: Any): Boolean = o match {
    case x: CompletionParams => (this.query == x.query) && (this.level == x.level)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.protocol.CompletionParams".##) + query.##) + level.##)
  }
  override def toString: String = {
    "CompletionParams(" + query + ", " + level + ")"
  }
  private[this] def copy(query: String = query, level: Option[Int] = level): CompletionParams = {
    new CompletionParams(query, level)
  }
  def withQuery(query: String): CompletionParams = {
    copy(query = query)
  }
  def withLevel(level: Option[Int]): CompletionParams = {
    copy(level = level)
  }
  def withLevel(level: Int): CompletionParams = {
    copy(level = Option(level))
  }
}
object CompletionParams {
  
  def apply(query: String): CompletionParams = new CompletionParams(query)
  def apply(query: String, level: Option[Int]): CompletionParams = new CompletionParams(query, level)
  def apply(query: String, level: Int): CompletionParams = new CompletionParams(query, Option(level))
}
