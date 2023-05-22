/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/** A workspace edit represents changes to many resources managed in the workspace. */
final class ScalaWorkspaceEdit private (
  val changes: Vector[sbt.internal.bsp.ScalaTextEdit]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ScalaWorkspaceEdit => (this.changes == x.changes)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.bsp.ScalaWorkspaceEdit".##) + changes.##)
  }
  override def toString: String = {
    "ScalaWorkspaceEdit(" + changes + ")"
  }
  private[this] def copy(changes: Vector[sbt.internal.bsp.ScalaTextEdit] = changes): ScalaWorkspaceEdit = {
    new ScalaWorkspaceEdit(changes)
  }
  def withChanges(changes: Vector[sbt.internal.bsp.ScalaTextEdit]): ScalaWorkspaceEdit = {
    copy(changes = changes)
  }
}
object ScalaWorkspaceEdit {
  
  def apply(changes: Vector[sbt.internal.bsp.ScalaTextEdit]): ScalaWorkspaceEdit = new ScalaWorkspaceEdit(changes)
}
