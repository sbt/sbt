/**
 * This code is generated using sbt-datatype.
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class SettingQueryResponse private (
  val value: String) extends sbt.protocol.EventMessage() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: SettingQueryResponse => (this.value == x.value)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (17 + value.##)
  }
  override def toString: String = {
    "SettingQueryResponse(" + value + ")"
  }
  protected[this] def copy(value: String = value): SettingQueryResponse = {
    new SettingQueryResponse(value)
  }
  def withValue(value: String): SettingQueryResponse = {
    copy(value = value)
  }
}
object SettingQueryResponse {
  
  def apply(value: String): SettingQueryResponse = new SettingQueryResponse(value)
}
