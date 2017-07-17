/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class SettingQueryFailure private (
  val message: String) extends sbt.protocol.SettingQueryResponse() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: SettingQueryFailure => (this.message == x.message)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.protocol.SettingQueryFailure".##) + message.##)
  }
  override def toString: String = {
    "SettingQueryFailure(" + message + ")"
  }
  protected[this] def copy(message: String = message): SettingQueryFailure = {
    new SettingQueryFailure(message)
  }
  def withMessage(message: String): SettingQueryFailure = {
    copy(message = message)
  }
}
object SettingQueryFailure {
  
  def apply(message: String): SettingQueryFailure = new SettingQueryFailure(message)
}
