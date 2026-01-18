/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker
final class CompileResponse private (
  val hasModified: Boolean) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: CompileResponse => (this.hasModified == x.hasModified)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.worker.CompileResponse".##) + hasModified.##)
  }
  override def toString: String = {
    "CompileResponse(" + hasModified + ")"
  }
  private def copy(hasModified: Boolean): CompileResponse = {
    new CompileResponse(hasModified)
  }
  def withHasModified(hasModified: Boolean): CompileResponse = {
    copy(hasModified = hasModified)
  }
}
object CompileResponse {
  
  def apply(hasModified: Boolean): CompileResponse = new CompileResponse(hasModified)
}
