/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * @param taskId Unique id of the task with optional reference to parent task id.
 * @param eventTime Optional timestamp of when the event started in milliseconds since Epoch.
 * @param message Message describing the task progress.
 * @param total If known, total amount of work units in this task.
 * @param progress If known, completed amount of work units in this task.
 * @param unit Name of a work unit. For example, "files" or "tests". May be empty.
 * @param dataKind Kind of data to expect in the `data` field.
 * @param data Optional metadata about the task.
 */
final class TaskProgressParams private (
  val taskId: sbt.internal.bsp.TaskId,
  val eventTime: Option[Long],
  val message: Option[String],
  val total: Option[Long],
  val progress: Option[Long],
  val unit: Option[String],
  val dataKind: Option[String],
  val data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: TaskProgressParams => (this.taskId == x.taskId) && (this.eventTime == x.eventTime) && (this.message == x.message) && (this.total == x.total) && (this.progress == x.progress) && (this.unit == x.unit) && (this.dataKind == x.dataKind) && (this.data == x.data)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.TaskProgressParams".##) + taskId.##) + eventTime.##) + message.##) + total.##) + progress.##) + unit.##) + dataKind.##) + data.##)
  }
  override def toString: String = {
    "TaskProgressParams(" + taskId + ", " + eventTime + ", " + message + ", " + total + ", " + progress + ", " + unit + ", " + dataKind + ", " + data + ")"
  }
  private[this] def copy(taskId: sbt.internal.bsp.TaskId = taskId, eventTime: Option[Long] = eventTime, message: Option[String] = message, total: Option[Long] = total, progress: Option[Long] = progress, unit: Option[String] = unit, dataKind: Option[String] = dataKind, data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue] = data): TaskProgressParams = {
    new TaskProgressParams(taskId, eventTime, message, total, progress, unit, dataKind, data)
  }
  def withTaskId(taskId: sbt.internal.bsp.TaskId): TaskProgressParams = {
    copy(taskId = taskId)
  }
  def withEventTime(eventTime: Option[Long]): TaskProgressParams = {
    copy(eventTime = eventTime)
  }
  def withEventTime(eventTime: Long): TaskProgressParams = {
    copy(eventTime = Option(eventTime))
  }
  def withMessage(message: Option[String]): TaskProgressParams = {
    copy(message = message)
  }
  def withMessage(message: String): TaskProgressParams = {
    copy(message = Option(message))
  }
  def withTotal(total: Option[Long]): TaskProgressParams = {
    copy(total = total)
  }
  def withTotal(total: Long): TaskProgressParams = {
    copy(total = Option(total))
  }
  def withProgress(progress: Option[Long]): TaskProgressParams = {
    copy(progress = progress)
  }
  def withProgress(progress: Long): TaskProgressParams = {
    copy(progress = Option(progress))
  }
  def withUnit(unit: Option[String]): TaskProgressParams = {
    copy(unit = unit)
  }
  def withUnit(unit: String): TaskProgressParams = {
    copy(unit = Option(unit))
  }
  def withDataKind(dataKind: Option[String]): TaskProgressParams = {
    copy(dataKind = dataKind)
  }
  def withDataKind(dataKind: String): TaskProgressParams = {
    copy(dataKind = Option(dataKind))
  }
  def withData(data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]): TaskProgressParams = {
    copy(data = data)
  }
  def withData(data: sjsonnew.shaded.scalajson.ast.unsafe.JValue): TaskProgressParams = {
    copy(data = Option(data))
  }
}
object TaskProgressParams {
  
  def apply(taskId: sbt.internal.bsp.TaskId, eventTime: Option[Long], message: Option[String], total: Option[Long], progress: Option[Long], unit: Option[String], dataKind: Option[String], data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]): TaskProgressParams = new TaskProgressParams(taskId, eventTime, message, total, progress, unit, dataKind, data)
  def apply(taskId: sbt.internal.bsp.TaskId, eventTime: Long, message: String, total: Long, progress: Long, unit: String, dataKind: String, data: sjsonnew.shaded.scalajson.ast.unsafe.JValue): TaskProgressParams = new TaskProgressParams(taskId, Option(eventTime), Option(message), Option(total), Option(progress), Option(unit), Option(dataKind), Option(data))
}
