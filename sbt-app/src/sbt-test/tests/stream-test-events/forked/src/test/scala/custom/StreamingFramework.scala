package custom

import sbt.testing._

trait StreamTest

final class SampleTest extends StreamTest

final class StreamingFramework extends Framework {
  def name(): String = "StreamingFramework"

  def fingerprints(): Array[Fingerprint] =
    Array(
      new SubclassFingerprint {
        def isModule(): Boolean = false
        def superclassName(): String = "custom.StreamTest"
        def requireNoArgConstructor(): Boolean = true
      }
    )

  def runner(
      args: Array[String],
      remoteArgs: Array[String],
      testClassLoader: ClassLoader
  ): Runner =
    new StreamingRunner
}

final class StreamingRunner extends Runner {
  def tasks(taskDefs: Array[TaskDef]): Array[Task] =
    taskDefs.map(new StreamingTask(_))

  def done(): String = ""

  def args(): Array[String] = Array.empty

  def remoteArgs(): Array[String] = Array.empty

  def receiveMessage(msg: String): Option[String] = None

  def serializeTask(task: Task, serializer: TaskDef => String): String =
    serializer(task.taskDef())

  def deserializeTask(task: String, deserializer: String => TaskDef): Task =
    new StreamingTask(deserializer(task))
}

final class StreamingTask(td: TaskDef) extends Task {
  def taskDef(): TaskDef = td

  def tags(): Array[String] = Array.empty

  def execute(handler: EventHandler, loggers: Array[Logger]): Array[Task] = {
    handler.handle(new StreamingEvent(td, "first"))
    Thread.sleep(1200L)
    handler.handle(new StreamingEvent(td, "second"))
    Array.empty
  }
}

final class StreamingEvent(td: TaskDef, testName: String) extends Event {
  def fullyQualifiedName(): String = td.fullyQualifiedName()
  def fingerprint(): Fingerprint = td.fingerprint()
  def selector(): Selector = new TestSelector(testName)
  def status(): Status = Status.Success
  def throwable(): OptionalThrowable = new OptionalThrowable()
  def duration(): Long = 0L
}