/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.librarymanagement
final class UpdateReportLite private (
  val configurations: Vector[sbt.internal.librarymanagement.ConfigurationReportLite]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: UpdateReportLite => (this.configurations == x.configurations)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.librarymanagement.UpdateReportLite".##) + configurations.##)
  }
  override def toString: String = {
    "UpdateReportLite(" + configurations + ")"
  }
  private[this] def copy(configurations: Vector[sbt.internal.librarymanagement.ConfigurationReportLite] = configurations): UpdateReportLite = {
    new UpdateReportLite(configurations)
  }
  def withConfigurations(configurations: Vector[sbt.internal.librarymanagement.ConfigurationReportLite]): UpdateReportLite = {
    copy(configurations = configurations)
  }
}
object UpdateReportLite {
  
  def apply(configurations: Vector[sbt.internal.librarymanagement.ConfigurationReportLite]): UpdateReportLite = new UpdateReportLite(configurations)
}
