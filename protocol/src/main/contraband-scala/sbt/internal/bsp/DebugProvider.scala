/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
final class DebugProvider private (
  val languageIds: Vector[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: DebugProvider => (this.languageIds == x.languageIds)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.bsp.DebugProvider".##) + languageIds.##)
  }
  override def toString: String = {
    "DebugProvider(" + languageIds + ")"
  }
  private[this] def copy(languageIds: Vector[String] = languageIds): DebugProvider = {
    new DebugProvider(languageIds)
  }
  def withLanguageIds(languageIds: Vector[String]): DebugProvider = {
    copy(languageIds = languageIds)
  }
}
object DebugProvider {
  
  def apply(languageIds: Vector[String]): DebugProvider = new DebugProvider(languageIds)
}
