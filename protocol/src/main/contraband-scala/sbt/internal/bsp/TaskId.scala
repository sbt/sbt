/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * @param id A unique identifier
 * @param parents The parent task ids, if any. A non-empty parents field means
                  this task is a sub-task of every parent task.
 */
final class TaskId private (
  val id: String,
  val parents: Vector[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: TaskId => (this.id == x.id) && (this.parents == x.parents)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.TaskId".##) + id.##) + parents.##)
  }
  override def toString: String = {
    "TaskId(" + id + ", " + parents + ")"
  }
  private[this] def copy(id: String = id, parents: Vector[String] = parents): TaskId = {
    new TaskId(id, parents)
  }
  def withId(id: String): TaskId = {
    copy(id = id)
  }
  def withParents(parents: Vector[String]): TaskId = {
    copy(parents = parents)
  }
}
object TaskId {
  
  def apply(id: String, parents: Vector[String]): TaskId = new TaskId(id, parents)
}
