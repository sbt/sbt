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
 */
final class InitializeBuildResult private (
  val displayName: String,
  val version: String,
  val bspVersion: String,
  val capabilities: sbt.internal.bsp.BuildClientCapabilities) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: InitializeBuildResult => (this.displayName == x.displayName) && (this.version == x.version) && (this.bspVersion == x.bspVersion) && (this.capabilities == x.capabilities)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.InitializeBuildResult".##) + displayName.##) + version.##) + bspVersion.##) + capabilities.##)
  }
  override def toString: String = {
    "InitializeBuildResult(" + displayName + ", " + version + ", " + bspVersion + ", " + capabilities + ")"
  }
  private[this] def copy(displayName: String = displayName, version: String = version, bspVersion: String = bspVersion, capabilities: sbt.internal.bsp.BuildClientCapabilities = capabilities): InitializeBuildResult = {
    new InitializeBuildResult(displayName, version, bspVersion, capabilities)
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
  def withCapabilities(capabilities: sbt.internal.bsp.BuildClientCapabilities): InitializeBuildResult = {
    copy(capabilities = capabilities)
  }
}
object InitializeBuildResult {
  
  def apply(displayName: String, version: String, bspVersion: String, capabilities: sbt.internal.bsp.BuildClientCapabilities): InitializeBuildResult = new InitializeBuildResult(displayName, version, bspVersion, capabilities)
}
