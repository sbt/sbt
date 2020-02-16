/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * Initialize Build Request
 * @param displayName Name of the client
 * @param version The version of the client
 * @param bspVersion The BSP version that the client speaks
 * @param rootUri The rootUri of the workspace
 * @param capabilities The capabilities of the client
 */
final class InitializeBuildParams private (
  val displayName: String,
  val version: String,
  val bspVersion: String,
  val rootUri: java.net.URI,
  val capabilities: sbt.internal.bsp.BuildClientCapabilities) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: InitializeBuildParams => (this.displayName == x.displayName) && (this.version == x.version) && (this.bspVersion == x.bspVersion) && (this.rootUri == x.rootUri) && (this.capabilities == x.capabilities)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.InitializeBuildParams".##) + displayName.##) + version.##) + bspVersion.##) + rootUri.##) + capabilities.##)
  }
  override def toString: String = {
    "InitializeBuildParams(" + displayName + ", " + version + ", " + bspVersion + ", " + rootUri + ", " + capabilities + ")"
  }
  private[this] def copy(displayName: String = displayName, version: String = version, bspVersion: String = bspVersion, rootUri: java.net.URI = rootUri, capabilities: sbt.internal.bsp.BuildClientCapabilities = capabilities): InitializeBuildParams = {
    new InitializeBuildParams(displayName, version, bspVersion, rootUri, capabilities)
  }
  def withDisplayName(displayName: String): InitializeBuildParams = {
    copy(displayName = displayName)
  }
  def withVersion(version: String): InitializeBuildParams = {
    copy(version = version)
  }
  def withBspVersion(bspVersion: String): InitializeBuildParams = {
    copy(bspVersion = bspVersion)
  }
  def withRootUri(rootUri: java.net.URI): InitializeBuildParams = {
    copy(rootUri = rootUri)
  }
  def withCapabilities(capabilities: sbt.internal.bsp.BuildClientCapabilities): InitializeBuildParams = {
    copy(capabilities = capabilities)
  }
}
object InitializeBuildParams {
  
  def apply(displayName: String, version: String, bspVersion: String, rootUri: java.net.URI, capabilities: sbt.internal.bsp.BuildClientCapabilities): InitializeBuildParams = new InitializeBuildParams(displayName, version, bspVersion, rootUri, capabilities)
}
