/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/**
 * Cross-versions a module using the result of
 * prepending `prefix` and appending `suffix` to the binary version.
 * For example, if `prefix = "foo_"` and `suffix = "_bar"` and the binary version is "2.10",
 * the module is cross-versioned with "foo_2.10_bar".
 */
final class Binary private (
  val prefix: String,
  val suffix: String) extends sbt.librarymanagement.CrossVersion() with Serializable {
  
  private def this() = this("", "")
  
  override def equals(o: Any): Boolean = o match {
    case x: Binary => (this.prefix == x.prefix) && (this.suffix == x.suffix)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "Binary".##) + prefix.##) + suffix.##)
  }
  override def toString: String = {
    "Binary(" + prefix + ", " + suffix + ")"
  }
  protected[this] def copy(prefix: String = prefix, suffix: String = suffix): Binary = {
    new Binary(prefix, suffix)
  }
  def withPrefix(prefix: String): Binary = {
    copy(prefix = prefix)
  }
  def withSuffix(suffix: String): Binary = {
    copy(suffix = suffix)
  }
}
object Binary {
  
  def apply(): Binary = new Binary("", "")
  def apply(prefix: String, suffix: String): Binary = new Binary(prefix, suffix)
}
