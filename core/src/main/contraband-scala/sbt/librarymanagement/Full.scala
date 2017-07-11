/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/**
 * Cross-versions a module with the result of
 * prepending `prefix` and appending `suffix` to the full version.
 * For example, if `prefix = "foo_"` and `suffix = "_bar"` and the full version is "2.12.1",
 * the module is cross-versioned with "foo_2.12.1_bar".
 */
final class Full private (
  val prefix: String,
  val suffix: String) extends sbt.librarymanagement.CrossVersion() with Serializable {
  
  private def this() = this("", "")
  
  override def equals(o: Any): Boolean = o match {
    case x: Full => (this.prefix == x.prefix) && (this.suffix == x.suffix)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "Full".##) + prefix.##) + suffix.##)
  }
  override def toString: String = {
    "Full(" + prefix + ", " + suffix + ")"
  }
  protected[this] def copy(prefix: String = prefix, suffix: String = suffix): Full = {
    new Full(prefix, suffix)
  }
  def withPrefix(prefix: String): Full = {
    copy(prefix = prefix)
  }
  def withSuffix(suffix: String): Full = {
    copy(suffix = suffix)
  }
}
object Full {
  
  def apply(): Full = new Full("", "")
  def apply(prefix: String, suffix: String): Full = new Full(prefix, suffix)
}
