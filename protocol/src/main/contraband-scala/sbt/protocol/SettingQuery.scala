/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class SettingQuery private (
  val setting: String) extends sbt.protocol.CommandMessage() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: SettingQuery => (this.setting == x.setting)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.protocol.SettingQuery".##) + setting.##)
  }
  override def toString: String = {
    "SettingQuery(" + setting + ")"
  }
  protected[this] def copy(setting: String = setting): SettingQuery = {
    new SettingQuery(setting)
  }
  def withSetting(setting: String): SettingQuery = {
    copy(setting = setting)
  }
}
object SettingQuery {
  
  def apply(setting: String): SettingQuery = new SettingQuery(setting)
}
