import scala.collection.mutable.ListBuffer

object EventLog {
  val logs = ListBuffer.empty[String]
  val errors = ListBuffer.empty[Throwable]

  def reset(): Unit = logs.clear()
}
