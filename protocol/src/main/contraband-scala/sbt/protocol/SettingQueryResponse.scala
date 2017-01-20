/**
 * This code is generated using sbt-datatype.
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class SettingQueryResponse private (
  val values: Vector[String]) extends sbt.protocol.EventMessage() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: SettingQueryResponse => (this.values == x.values)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (17 + values.##)
  }
  override def toString: String = {
    "SettingQueryResponse(" + values + ")"
  }
  protected[this] def copy(values: Vector[String] = values): SettingQueryResponse = {
    new SettingQueryResponse(values)
  }
  def withValues(values: Vector[String]): SettingQueryResponse = {
    copy(values = values)
  }
}
object SettingQueryResponse {
  
  def apply(values: Vector[String]): SettingQueryResponse = new SettingQueryResponse(values)
}
