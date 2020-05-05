/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * @param displayName Name of the server
 * @param version The version of the server
 * @param bspVersion The BSP version that the server speaks
 * @param capabilities The capabilities of the build server
 * @param data Additional metadata about the server
 */
final class InitializeBuildResult private (
  val displayName: String,
  val version: String,
  val bspVersion: String,
  val capabilities: sbt.internal.bsp.BuildServerCapabilities,
  val data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: InitializeBuildResult => (this.displayName == x.displayName) && (this.version == x.version) && (this.bspVersion == x.bspVersion) && (this.capabilities == x.capabilities) && (this.data == x.data)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.InitializeBuildResult".##) + displayName.##) + version.##) + bspVersion.##) + capabilities.##) + data.##)
  }
  override def toString: String = {
    "InitializeBuildResult(" + displayName + ", " + version + ", " + bspVersion + ", " + capabilities + ", " + data + ")"
  }
  private[this] def copy(displayName: String = displayName, version: String = version, bspVersion: String = bspVersion, capabilities: sbt.internal.bsp.BuildServerCapabilities = capabilities, data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue] = data): InitializeBuildResult = {
    new InitializeBuildResult(displayName, version, bspVersion, capabilities, data)
  }
  def withDisplayName(displayName: String): InitializeBuildResult = {
    copy(displayName = displayName)
  }
  def withVersion(version: String): InitializeBuildResult = {
    copy(version = version)
  }
  def withBspVersion(bspVersion: String): InitializeBuildResult = {
    copy(bspVersion = bspVersion)
  }
  def withCapabilities(capabilities: sbt.internal.bsp.BuildServerCapabilities): InitializeBuildResult = {
    copy(capabilities = capabilities)
  }
  def withData(data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]): InitializeBuildResult = {
    copy(data = data)
  }
  def withData(data: sjsonnew.shaded.scalajson.ast.unsafe.JValue): InitializeBuildResult = {
    copy(data = Option(data))
  }
}
object InitializeBuildResult {
  
  def apply(displayName: String, version: String, bspVersion: String, capabilities: sbt.internal.bsp.BuildServerCapabilities, data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]): InitializeBuildResult = new InitializeBuildResult(displayName, version, bspVersion, capabilities, data)
  def apply(displayName: String, version: String, bspVersion: String, capabilities: sbt.internal.bsp.BuildServerCapabilities, data: sjsonnew.shaded.scalajson.ast.unsafe.JValue): InitializeBuildResult = new InitializeBuildResult(displayName, version, bspVersion, capabilities, Option(data))
}
