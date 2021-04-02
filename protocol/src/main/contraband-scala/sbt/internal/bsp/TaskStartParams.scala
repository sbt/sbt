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
 * @param dataKind Kind of data to expect in the `data` field.
 * @param data Optional metadata about the task.
 */
final class TaskStartParams private (
  val taskId: sbt.internal.bsp.TaskId,
  val eventTime: Option[Long],
  val message: Option[String],
  val dataKind: Option[String],
  val data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: TaskStartParams => (this.taskId == x.taskId) && (this.eventTime == x.eventTime) && (this.message == x.message) && (this.dataKind == x.dataKind) && (this.data == x.data)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.TaskStartParams".##) + taskId.##) + eventTime.##) + message.##) + dataKind.##) + data.##)
  }
  override def toString: String = {
    "TaskStartParams(" + taskId + ", " + eventTime + ", " + message + ", " + dataKind + ", " + data + ")"
  }
  private[this] def copy(taskId: sbt.internal.bsp.TaskId = taskId, eventTime: Option[Long] = eventTime, message: Option[String] = message, dataKind: Option[String] = dataKind, data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue] = data): TaskStartParams = {
    new TaskStartParams(taskId, eventTime, message, dataKind, data)
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
  def withDataKind(dataKind: Option[String]): TaskStartParams = {
    copy(dataKind = dataKind)
  }
  def withDataKind(dataKind: String): TaskStartParams = {
    copy(dataKind = Option(dataKind))
  }
  def withData(data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]): TaskStartParams = {
    copy(data = data)
  }
  def withData(data: sjsonnew.shaded.scalajson.ast.unsafe.JValue): TaskStartParams = {
    copy(data = Option(data))
  }
}
object TaskStartParams {
  
  def apply(taskId: sbt.internal.bsp.TaskId, eventTime: Option[Long], message: Option[String], dataKind: Option[String], data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]): TaskStartParams = new TaskStartParams(taskId, eventTime, message, dataKind, data)
  def apply(taskId: sbt.internal.bsp.TaskId, eventTime: Long, message: String, dataKind: String, data: sjsonnew.shaded.scalajson.ast.unsafe.JValue): TaskStartParams = new TaskStartParams(taskId, Option(eventTime), Option(message), Option(dataKind), Option(data))
}
