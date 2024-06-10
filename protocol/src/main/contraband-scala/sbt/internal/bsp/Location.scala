/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/** Represents a location inside a resource, such as a line inside a text file. */
final class Location private (
  val uri: String,
  val range: sbt.internal.bsp.Range) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: Location => (this.uri == x.uri) && (this.range == x.range)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.Location".##) + uri.##) + range.##)
  }
  override def toString: String = {
    "Location(" + uri + ", " + range + ")"
  }
  private[this] def copy(uri: String = uri, range: sbt.internal.bsp.Range = range): Location = {
    new Location(uri, range)
  }
  def withUri(uri: String): Location = {
    copy(uri = uri)
  }
  def withRange(range: sbt.internal.bsp.Range): Location = {
    copy(range = range)
  }
}
object Location {
  
  def apply(uri: String, range: sbt.internal.bsp.Range): Location = new Location(uri, range)
}
