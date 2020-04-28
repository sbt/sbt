/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * Task Notifications
 * @param taskId Unique id of the task with optional reference to parent task id.
 * @param eventTime Optional timestamp of when the event started in milliseconds since Epoch.
 * @param message Optional message describing the task.
 */
final class TaskStartParams private (
  val taskId: sbt.internal.bsp.TaskId,
  val eventTime: Option[Long],
  val message: Option[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: TaskStartParams => (this.taskId == x.taskId) && (this.eventTime == x.eventTime) && (this.message == x.message)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.TaskStartParams".##) + taskId.##) + eventTime.##) + message.##)
  }
  override def toString: String = {
    "TaskStartParams(" + taskId + ", " + eventTime + ", " + message + ")"
  }
  private[this] def copy(taskId: sbt.internal.bsp.TaskId = taskId, eventTime: Option[Long] = eventTime, message: Option[String] = message): TaskStartParams = {
    new TaskStartParams(taskId, eventTime, message)
  }
  def withTaskId(taskId: sbt.internal.bsp.TaskId): TaskStartParams = {
    copy(taskId = taskId)
  }
  def withEventTime(eventTime: Option[Long]): TaskStartParams = {
    copy(eventTime = eventTime)
  }
  def withEventTime(eventTime: Long): TaskStartParams = {
    copy(eventTime = Option(eventTime))
  }
  def withMessage(message: Option[String]): TaskStartParams = {
    copy(message = message)
  }
  def withMessage(message: String): TaskStartParams = {
    copy(message = Option(message))
  }
}
object TaskStartParams {
  
  def apply(taskId: sbt.internal.bsp.TaskId, eventTime: Option[Long], message: Option[String]): TaskStartParams = new TaskStartParams(taskId, eventTime, message)
  def apply(taskId: sbt.internal.bsp.TaskId, eventTime: Long, message: String): TaskStartParams = new TaskStartParams(taskId, Option(eventTime), Option(message))
}
