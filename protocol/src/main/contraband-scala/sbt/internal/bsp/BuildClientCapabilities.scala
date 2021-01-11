/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/** @param languageIds The languages that this client supports.
The ID strings for each language is defined in the LSP.
The server must never respond with build targets for other
languages than those that appear in this list. */
final class BuildClientCapabilities private (
  val languageIds: Vector[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: BuildClientCapabilities => (this.languageIds == x.languageIds)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.bsp.BuildClientCapabilities".##) + languageIds.##)
  }
  override def toString: String = {
    "BuildClientCapabilities(" + languageIds + ")"
  }
  private[this] def copy(languageIds: Vector[String] = languageIds): BuildClientCapabilities = {
    new BuildClientCapabilities(languageIds)
  }
  def withLanguageIds(languageIds: Vector[String]): BuildClientCapabilities = {
    copy(languageIds = languageIds)
  }
}
object BuildClientCapabilities {
  
  def apply(languageIds: Vector[String]): BuildClientCapabilities = new BuildClientCapabilities(languageIds)
}
