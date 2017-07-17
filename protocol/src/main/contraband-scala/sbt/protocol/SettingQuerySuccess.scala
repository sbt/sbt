/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class SettingQuerySuccess private (
  val value: sjsonnew.shaded.scalajson.ast.unsafe.JValue,
  val contentType: String) extends sbt.protocol.SettingQueryResponse() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: SettingQuerySuccess => (this.value == x.value) && (this.contentType == x.contentType)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.protocol.SettingQuerySuccess".##) + value.##) + contentType.##)
  }
  override def toString: String = {
    "SettingQuerySuccess(" + value + ", " + contentType + ")"
  }
  protected[this] def copy(value: sjsonnew.shaded.scalajson.ast.unsafe.JValue = value, contentType: String = contentType): SettingQuerySuccess = {
    new SettingQuerySuccess(value, contentType)
  }
  def withValue(value: sjsonnew.shaded.scalajson.ast.unsafe.JValue): SettingQuerySuccess = {
    copy(value = value)
  }
  def withContentType(contentType: String): SettingQuerySuccess = {
    copy(contentType = contentType)
  }
}
object SettingQuerySuccess {
  
  def apply(value: sjsonnew.shaded.scalajson.ast.unsafe.JValue, contentType: String): SettingQuerySuccess = new SettingQuerySuccess(value, contentType)
}
