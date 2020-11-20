/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * Scala Test Params
 * ScalaTestParams contains scala-specific metadata for testing Scala targets.
 * This metadata is embedded in the data field of the buildTarget/test request
 * when the dataKind field contains "scala-test".
 */
final class ScalaTestParams private (
  val testClasses: Vector[sbt.internal.bsp.ScalaTestClassesItem]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: ScalaTestParams => (this.testClasses == x.testClasses)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.bsp.ScalaTestParams".##) + testClasses.##)
  }
  override def toString: String = {
    "ScalaTestParams(" + testClasses + ")"
  }
  private[this] def copy(testClasses: Vector[sbt.internal.bsp.ScalaTestClassesItem] = testClasses): ScalaTestParams = {
    new ScalaTestParams(testClasses)
  }
  def withTestClasses(testClasses: Vector[sbt.internal.bsp.ScalaTestClassesItem]): ScalaTestParams = {
    copy(testClasses = testClasses)
  }
}
object ScalaTestParams {
  
  def apply(testClasses: Vector[sbt.internal.bsp.ScalaTestClassesItem]): ScalaTestParams = new ScalaTestParams(testClasses)
}
