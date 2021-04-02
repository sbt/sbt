/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
final class TestProvider private (
  val languageIds: Vector[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: TestProvider => (this.languageIds == x.languageIds)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.bsp.TestProvider".##) + languageIds.##)
  }
  override def toString: String = {
    "TestProvider(" + languageIds + ")"
  }
  private[this] def copy(languageIds: Vector[String] = languageIds): TestProvider = {
    new TestProvider(languageIds)
  }
  def withLanguageIds(languageIds: Vector[String]): TestProvider = {
    copy(languageIds = languageIds)
  }
}
object TestProvider {
  
  def apply(languageIds: Vector[String]): TestProvider = new TestProvider(languageIds)
}
