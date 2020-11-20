/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class CompletionResponse private (
  val items: Vector[String],
  val cachedMainClassNames: Option[Boolean],
  val cachedTestNames: Option[Boolean]) extends Serializable {
  
  private def this(items: Vector[String]) = this(items, None, None)
  
  override def equals(o: Any): Boolean = o match {
    case x: CompletionResponse => (this.items == x.items) && (this.cachedMainClassNames == x.cachedMainClassNames) && (this.cachedTestNames == x.cachedTestNames)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.protocol.CompletionResponse".##) + items.##) + cachedMainClassNames.##) + cachedTestNames.##)
  }
  override def toString: String = {
    "CompletionResponse(" + items + ", " + cachedMainClassNames + ", " + cachedTestNames + ")"
  }
  private[this] def copy(items: Vector[String] = items, cachedMainClassNames: Option[Boolean] = cachedMainClassNames, cachedTestNames: Option[Boolean] = cachedTestNames): CompletionResponse = {
    new CompletionResponse(items, cachedMainClassNames, cachedTestNames)
  }
  def withItems(items: Vector[String]): CompletionResponse = {
    copy(items = items)
  }
  def withCachedMainClassNames(cachedMainClassNames: Option[Boolean]): CompletionResponse = {
    copy(cachedMainClassNames = cachedMainClassNames)
  }
  def withCachedMainClassNames(cachedMainClassNames: Boolean): CompletionResponse = {
    copy(cachedMainClassNames = Option(cachedMainClassNames))
  }
  def withCachedTestNames(cachedTestNames: Option[Boolean]): CompletionResponse = {
    copy(cachedTestNames = cachedTestNames)
  }
  def withCachedTestNames(cachedTestNames: Boolean): CompletionResponse = {
    copy(cachedTestNames = Option(cachedTestNames))
  }
}
object CompletionResponse {
  
  def apply(items: Vector[String]): CompletionResponse = new CompletionResponse(items)
  def apply(items: Vector[String], cachedMainClassNames: Option[Boolean], cachedTestNames: Option[Boolean]): CompletionResponse = new CompletionResponse(items, cachedMainClassNames, cachedTestNames)
  def apply(items: Vector[String], cachedMainClassNames: Boolean, cachedTestNames: Boolean): CompletionResponse = new CompletionResponse(items, Option(cachedMainClassNames), Option(cachedTestNames))
}
