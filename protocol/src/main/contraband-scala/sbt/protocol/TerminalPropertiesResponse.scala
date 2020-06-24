/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class TerminalPropertiesResponse private (
  val width: Int,
  val height: Int,
  val isAnsiSupported: Boolean,
  val isColorEnabled: Boolean,
  val isSupershellEnabled: Boolean,
  val isEchoEnabled: Boolean) extends sbt.protocol.EventMessage() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: TerminalPropertiesResponse => (this.width == x.width) && (this.height == x.height) && (this.isAnsiSupported == x.isAnsiSupported) && (this.isColorEnabled == x.isColorEnabled) && (this.isSupershellEnabled == x.isSupershellEnabled) && (this.isEchoEnabled == x.isEchoEnabled)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.protocol.TerminalPropertiesResponse".##) + width.##) + height.##) + isAnsiSupported.##) + isColorEnabled.##) + isSupershellEnabled.##) + isEchoEnabled.##)
  }
  override def toString: String = {
    "TerminalPropertiesResponse(" + width + ", " + height + ", " + isAnsiSupported + ", " + isColorEnabled + ", " + isSupershellEnabled + ", " + isEchoEnabled + ")"
  }
  private[this] def copy(width: Int = width, height: Int = height, isAnsiSupported: Boolean = isAnsiSupported, isColorEnabled: Boolean = isColorEnabled, isSupershellEnabled: Boolean = isSupershellEnabled, isEchoEnabled: Boolean = isEchoEnabled): TerminalPropertiesResponse = {
    new TerminalPropertiesResponse(width, height, isAnsiSupported, isColorEnabled, isSupershellEnabled, isEchoEnabled)
  }
  def withWidth(width: Int): TerminalPropertiesResponse = {
    copy(width = width)
  }
  def withHeight(height: Int): TerminalPropertiesResponse = {
    copy(height = height)
  }
  def withIsAnsiSupported(isAnsiSupported: Boolean): TerminalPropertiesResponse = {
    copy(isAnsiSupported = isAnsiSupported)
  }
  def withIsColorEnabled(isColorEnabled: Boolean): TerminalPropertiesResponse = {
    copy(isColorEnabled = isColorEnabled)
  }
  def withIsSupershellEnabled(isSupershellEnabled: Boolean): TerminalPropertiesResponse = {
    copy(isSupershellEnabled = isSupershellEnabled)
  }
  def withIsEchoEnabled(isEchoEnabled: Boolean): TerminalPropertiesResponse = {
    copy(isEchoEnabled = isEchoEnabled)
  }
}
object TerminalPropertiesResponse {
  
  def apply(width: Int, height: Int, isAnsiSupported: Boolean, isColorEnabled: Boolean, isSupershellEnabled: Boolean, isEchoEnabled: Boolean): TerminalPropertiesResponse = new TerminalPropertiesResponse(width, height, isAnsiSupported, isColorEnabled, isSupershellEnabled, isEchoEnabled)
}
