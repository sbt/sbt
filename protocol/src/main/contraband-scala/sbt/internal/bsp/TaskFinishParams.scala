/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * @param taskId Unique id of the task with optional reference to parent task id.
 * @param eventTime Optional timestamp of when the event started in milliseconds since Epoch.
 * @param message Optional message describing the task.
 * @param status Task completion status: 1 -> success, 2 -> error, 3 -> cancelled
 */
final class TaskFinishParams private (
  val taskId: sbt.internal.bsp.TaskId,
  val eventTime: Option[Long],
  val message: Option[String],
  val status: Int) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: TaskFinishParams => (this.taskId == x.taskId) && (this.eventTime == x.eventTime) && (this.message == x.message) && (this.status == x.status)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.TaskFinishParams".##) + taskId.##) + eventTime.##) + message.##) + status.##)
  }
  override def toString: String = {
    "TaskFinishParams(" + taskId + ", " + eventTime + ", " + message + ", " + status + ")"
  }
  private[this] def copy(taskId: sbt.internal.bsp.TaskId = taskId, eventTime: Option[Long] = eventTime, message: Option[String] = message, status: Int = status): TaskFinishParams = {
    new TaskFinishParams(taskId, eventTime, message, status)
  }
  def withTaskId(taskId: sbt.internal.bsp.TaskId): TaskFinishParams = {
    copy(taskId = taskId)
  }
  def withEventTime(eventTime: Option[Long]): TaskFinishParams = {
    copy(eventTime = eventTime)
  }
  def withEventTime(eventTime: Long): TaskFinishParams = {
    copy(eventTime = Option(eventTime))
  }
  def withMessage(message: Option[String]): TaskFinishParams = {
    copy(message = message)
  }
  def withMessage(message: String): TaskFinishParams = {
    copy(message = Option(message))
  }
  def withStatus(status: Int): TaskFinishParams = {
    copy(status = status)
  }
}
object TaskFinishParams {
  
  def apply(taskId: sbt.internal.bsp.TaskId, eventTime: Option[Long], message: Option[String], status: Int): TaskFinishParams = new TaskFinishParams(taskId, eventTime, message, status)
  def apply(taskId: sbt.internal.bsp.TaskId, eventTime: Long, message: String, status: Int): TaskFinishParams = new TaskFinishParams(taskId, Option(eventTime), Option(message), status)
}
