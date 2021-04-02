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
 * @param dataKind Kind of data to expect in the `data` field.
 * @param data Optional metadata about the task.
 */
final class TaskFinishParams private (
  val taskId: sbt.internal.bsp.TaskId,
  val eventTime: Option[Long],
  val message: Option[String],
  val status: Int,
  val dataKind: Option[String],
  val data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: TaskFinishParams => (this.taskId == x.taskId) && (this.eventTime == x.eventTime) && (this.message == x.message) && (this.status == x.status) && (this.dataKind == x.dataKind) && (this.data == x.data)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.TaskFinishParams".##) + taskId.##) + eventTime.##) + message.##) + status.##) + dataKind.##) + data.##)
  }
  override def toString: String = {
    "TaskFinishParams(" + taskId + ", " + eventTime + ", " + message + ", " + status + ", " + dataKind + ", " + data + ")"
  }
  private[this] def copy(taskId: sbt.internal.bsp.TaskId = taskId, eventTime: Option[Long] = eventTime, message: Option[String] = message, status: Int = status, dataKind: Option[String] = dataKind, data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue] = data): TaskFinishParams = {
    new TaskFinishParams(taskId, eventTime, message, status, dataKind, data)
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
  def withDataKind(dataKind: Option[String]): TaskFinishParams = {
    copy(dataKind = dataKind)
  }
  def withDataKind(dataKind: String): TaskFinishParams = {
    copy(dataKind = Option(dataKind))
  }
  def withData(data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]): TaskFinishParams = {
    copy(data = data)
  }
  def withData(data: sjsonnew.shaded.scalajson.ast.unsafe.JValue): TaskFinishParams = {
    copy(data = Option(data))
  }
}
object TaskFinishParams {
  
  def apply(taskId: sbt.internal.bsp.TaskId, eventTime: Option[Long], message: Option[String], status: Int, dataKind: Option[String], data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]): TaskFinishParams = new TaskFinishParams(taskId, eventTime, message, status, dataKind, data)
  def apply(taskId: sbt.internal.bsp.TaskId, eventTime: Long, message: String, status: Int, dataKind: String, data: sjsonnew.shaded.scalajson.ast.unsafe.JValue): TaskFinishParams = new TaskFinishParams(taskId, Option(eventTime), Option(message), status, Option(dataKind), Option(data))
}
