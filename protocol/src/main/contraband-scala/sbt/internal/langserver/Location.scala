/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
/** Represents a location inside a resource, such as a line inside a text file. */
final class Location private (
  val uri: String,
  val range: sbt.internal.langserver.Range) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: Location => (this.uri == x.uri) && (this.range == x.range)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.langserver.Location".##) + uri.##) + range.##)
  }
  override def toString: String = {
    "Location(" + uri + ", " + range + ")"
  }
  protected[this] def copy(uri: String = uri, range: sbt.internal.langserver.Range = range): Location = {
    new Location(uri, range)
  }
  def withUri(uri: String): Location = {
    copy(uri = uri)
  }
  def withRange(range: sbt.internal.langserver.Range): Location = {
    copy(range = range)
  }
}
object Location {
  
  def apply(uri: String, range: sbt.internal.langserver.Range): Location = new Location(uri, range)
}
