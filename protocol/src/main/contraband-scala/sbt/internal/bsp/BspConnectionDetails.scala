/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * https://build-server-protocol.github.io/docs/server-discovery.html
 * @param name The name of the build tool
 * @param version The version of the build tool
 * @param bspVersion The bsp version of the build tool
 * @param languages A collection of languages supported by this BSP server
 * @param argv Command arguments runnable via system processes to start a BSP server
 */
final class BspConnectionDetails private (
  val name: String,
  val version: String,
  val bspVersion: String,
  val languages: Vector[String],
  val argv: Vector[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: BspConnectionDetails => (this.name == x.name) && (this.version == x.version) && (this.bspVersion == x.bspVersion) && (this.languages == x.languages) && (this.argv == x.argv)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.BspConnectionDetails".##) + name.##) + version.##) + bspVersion.##) + languages.##) + argv.##)
  }
  override def toString: String = {
    "BspConnectionDetails(" + name + ", " + version + ", " + bspVersion + ", " + languages + ", " + argv + ")"
  }
  private[this] def copy(name: String = name, version: String = version, bspVersion: String = bspVersion, languages: Vector[String] = languages, argv: Vector[String] = argv): BspConnectionDetails = {
    new BspConnectionDetails(name, version, bspVersion, languages, argv)
  }
  def withName(name: String): BspConnectionDetails = {
    copy(name = name)
  }
  def withVersion(version: String): BspConnectionDetails = {
    copy(version = version)
  }
  def withBspVersion(bspVersion: String): BspConnectionDetails = {
    copy(bspVersion = bspVersion)
  }
  def withLanguages(languages: Vector[String]): BspConnectionDetails = {
    copy(languages = languages)
  }
  def withArgv(argv: Vector[String]): BspConnectionDetails = {
    copy(argv = argv)
  }
}
object BspConnectionDetails {
  
  def apply(name: String, version: String, bspVersion: String, languages: Vector[String], argv: Vector[String]): BspConnectionDetails = new BspConnectionDetails(name, version, bspVersion, languages, argv)
}
