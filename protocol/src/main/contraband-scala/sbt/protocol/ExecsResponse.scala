/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class ExecsResponse private (
  val execs: Vector[sbt.protocol.ExecInfo]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ExecsResponse => (this.execs == x.execs)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.protocol.ExecsResponse".##) + execs.##)
  }
  override def toString: String = {
    "ExecsResponse(" + execs + ")"
  }
  private def copy(execs: Vector[sbt.protocol.ExecInfo]): ExecsResponse = {
    new ExecsResponse(execs)
  }
  def withExecs(execs: Vector[sbt.protocol.ExecInfo]): ExecsResponse = {
    copy(execs = execs)
  }
}
object ExecsResponse {
  
  def apply(execs: Vector[sbt.protocol.ExecInfo]): ExecsResponse = new ExecsResponse(execs)
}
