/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import Def.{ Initialize, ScopedKey }
import Previous._
import sbt.internal.util.{ ~>, IMap, RMap }
import sbt.util.{ Input, Output, StampedFormat }
import sjsonnew.JsonFormat
import Scope.Global
import scala.util.control.NonFatal

/**
 * Reads the previous value of tasks on-demand.  The read values are cached so that they are only read once per task execution.
 * `referenced` provides the `Format` to use for each key.
 */
private[sbt] final class Previous(streams: Streams, referenced: IMap[ScopedTaskKey, Referenced]) {
  private[this] val map = referenced.mapValues(toValue)
  private[this] def toValue = λ[Referenced ~> ReferencedValue](new ReferencedValue(_))

  private[this] final class ReferencedValue[T](referenced: Referenced[T]) {
    import referenced.{ stamped, task }
    lazy val previousValue: Option[T] = {
      val in = streams(task).getInput(task, StreamName)
      try read(in, stamped)
      finally in.close()
    }
  }

  /** Used by the .previous runtime implementation to get the previous value for task `key`. */
  private def get[T](key: ScopedKey[Task[T]]): Option[T] =
    map.get(key).flatMap(_.previousValue)
}
object Previous {
  import sjsonnew.BasicJsonProtocol.StringJsonFormat
  private[sbt] type ScopedTaskKey[T] = ScopedKey[Task[T]]
  private type Streams = sbt.std.Streams[ScopedKey[_]]

  /** The stream where the task value is persisted. */
  private final val StreamName = "previous"

  /** Represents a reference task.previous*/
  private[sbt] final class Referenced[T](val task: ScopedKey[Task[T]], val format: JsonFormat[T]) {
    lazy val stamped = StampedFormat.withStamp(task.key.manifest.toString)(format)
    def setTask(newTask: ScopedKey[Task[T]]) = new Referenced(newTask, format)
  }

  private[sbt] val references = SettingKey[References](
    "previous-references",
    "Collects all static references to previous values of tasks.",
    KeyRanks.Invisible)
  private[sbt] val cache = TaskKey[Previous](
    "previous-cache",
    "Caches previous values of tasks read from disk for the duration of a task execution.",
    KeyRanks.Invisible)

  /** Records references to previous task value. This should be completely populated after settings finish loading. */
  private[sbt] final class References {
    private[this] var map = IMap.empty[ScopedTaskKey, Referenced]

    // TODO: this arbitrarily chooses a JsonFormat.
    // The need to choose is a fundamental problem with this approach, but this should at least make a stable choice.
    def recordReference[T](key: ScopedKey[Task[T]], format: JsonFormat[T]): Unit = synchronized {
      map = map.put(key, new Referenced(key, format))
    }
    def getReferences: IMap[ScopedTaskKey, Referenced] = synchronized { map }
  }

  /** Persists values of tasks t where there is some task referencing it via t.previous. */
  private[sbt] def complete(referenced: References,
                            results: RMap[Task, Result],
                            streams: Streams): Unit = {
    val map = referenced.getReferences
    def impl[T](key: ScopedKey[_], result: T): Unit =
      for (i <- map.get(key.asInstanceOf[ScopedTaskKey[T]])) {
        val out = streams.apply(i.task).getOutput(StreamName)
        try write(out, i.stamped, result)
        finally out.close()
      }

    for {
      results.TPair(Task(info, _), Value(result)) <- results.toTypedSeq
      key <- info.attributes get Def.taskDefinitionKey
    } impl(key, result)
  }

  private def read[T](input: Input, format: JsonFormat[T]): Option[T] =
    try Some(input.read()(format))
    catch { case NonFatal(_) => None }

  private def write[T](output: Output, format: JsonFormat[T], value: T): Unit =
    try output.write(value)(format)
    catch { case NonFatal(_) => () }

  /** Public as a macro implementation detail.  Do not call directly. */
  def runtime[T](skey: TaskKey[T])(implicit format: JsonFormat[T]): Initialize[Task[Option[T]]] = {
    val inputs = (cache in Global) zip Def.validated(skey, selfRefOk = true) zip (references in Global)
    inputs {
      case ((prevTask, resolved), refs) =>
        refs.recordReference(resolved, format) // always evaluated on project load
        import std.TaskExtra._
        prevTask.map(_ get resolved) // evaluated if this task is evaluated
    }
  }
}
