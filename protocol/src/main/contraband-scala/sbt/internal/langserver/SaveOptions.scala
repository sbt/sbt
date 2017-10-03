/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
final class SaveOptions private (
  /** The client is supposed to include the content on save. */
  val includeText: Option[Boolean]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: SaveOptions => (this.includeText == x.includeText)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.langserver.SaveOptions".##) + includeText.##)
  }
  override def toString: String = {
    "SaveOptions(" + includeText + ")"
  }
  protected[this] def copy(includeText: Option[Boolean] = includeText): SaveOptions = {
    new SaveOptions(includeText)
  }
  def withIncludeText(includeText: Option[Boolean]): SaveOptions = {
    copy(includeText = includeText)
  }
  def withIncludeText(includeText: Boolean): SaveOptions = {
    copy(includeText = Option(includeText))
  }
}
object SaveOptions {
  
  def apply(includeText: Option[Boolean]): SaveOptions = new SaveOptions(includeText)
  def apply(includeText: Boolean): SaveOptions = new SaveOptions(Option(includeText))
}
