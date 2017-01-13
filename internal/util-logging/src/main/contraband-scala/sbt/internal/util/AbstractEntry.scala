/**
 * This code is generated using sbt-datatype.
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util
abstract class AbstractEntry(
  val channelName: Option[String],
  val execId: Option[String]) extends Serializable {
  
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: AbstractEntry => (this.channelName == x.channelName) && (this.execId == x.execId)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + channelName.##) + execId.##)
  }
  override def toString: String = {
    "AbstractEntry(" + channelName + ", " + execId + ")"
  }
}
object AbstractEntry {
  
}
