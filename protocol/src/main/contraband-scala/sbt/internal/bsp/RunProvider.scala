/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
final class RunProvider private (
  val languageIds: Vector[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: RunProvider => (this.languageIds == x.languageIds)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.bsp.RunProvider".##) + languageIds.##)
  }
  override def toString: String = {
    "RunProvider(" + languageIds + ")"
  }
  private[this] def copy(languageIds: Vector[String] = languageIds): RunProvider = {
    new RunProvider(languageIds)
  }
  def withLanguageIds(languageIds: Vector[String]): RunProvider = {
    copy(languageIds = languageIds)
  }
}
object RunProvider {
  
  def apply(languageIds: Vector[String]): RunProvider = new RunProvider(languageIds)
}
