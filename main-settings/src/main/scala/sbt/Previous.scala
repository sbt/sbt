/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.Def.{ Initialize, ScopedKey }
import sbt.Previous._
import sbt.Scope.Global
import sbt.SlashSyntax0.given
import sbt.internal.util._
import sbt.std.TaskExtra._
import sbt.util.StampedFormat
import sjsonnew.JsonFormat

import scala.util.control.NonFatal

/**
 * Reads the previous value of tasks on-demand.  The read values are cached so that they are only read once per task execution.
 * `referenced` provides the `Format` to use for each key.
 */
private[sbt] final class Previous(streams: Streams, referenced: IMap[Previous.Key, Referenced]) {
  private var map = IMap.empty[Previous.Key, ReferencedValue]
  // We can't use mapValues to transform the map because mapValues is lazy and evaluates the
  // transformation function every time a value is fetched from the map, defeating the entire
  // purpose of ReferencedValue.
  for case referenced.TPair(k, v) <- referenced.toTypedSeq do
    map = map.put(k, new ReferencedValue(v))

  private final class ReferencedValue[T](referenced: Referenced[T]) {
    lazy val previousValue: Option[T] = referenced.read(streams)
  }

  /** Used by the .previous runtime implementation to get the previous value for task `key`. */
  private def get[T](key: Key[T]): Option[T] =
    map.get(key).flatMap(_.previousValue)
}
object Previous {
  import sjsonnew.BasicJsonProtocol.StringJsonFormat
  private[sbt] type ScopedTaskKey[T] = ScopedKey[Task[T]]
  private type AnyTaskKey = ScopedTaskKey[Any]
  private type Streams = sbt.std.Streams[ScopedKey[?]]

  /** The stream where the task value is persisted. */
  private final val StreamName = "previous"
  private[sbt] final val DependencyDirectory = "previous-dependencies"

  /** Represents a reference task.previous */
  private[sbt] final class Referenced[T](val key: Key[T], val format: JsonFormat[T]) {
    def this(task: ScopedTaskKey[T], format: JsonFormat[T]) = this(Key(task, task), format)

    // @deprecated("unused", "1.3.0")
    // private[sbt] def task: ScopedKey[Task[T]] = key.task

    lazy val stamped: JsonFormat[T] =
      StampedFormat.withStamp(key.task.key.tag.toString)(format)

    def setTask(newTask: ScopedKey[Task[T]]) = new Referenced(newTask, format)
    private[sbt] def read(streams: Streams): Option[T] =
      try Option(streams(key.cacheKey).cacheStoreFactory.make(StreamName).read[T]()(using stamped))
      catch { case NonFatal(_) => None }
  }

  private[sbt] val references = SettingKey[References](
    "previous-references",
    "Collects all static references to previous values of tasks.",
    KeyRanks.Invisible
  )
  private[sbt] val cache = TaskKey[Previous](
    "previous-cache",
    "Caches previous values of tasks read from disk for the duration of a task execution.",
    KeyRanks.Invisible
  )

  private[sbt] class Key[T](val task: ScopedKey[Task[T]], val enclosing: AnyTaskKey) {
    override def equals(o: Any): Boolean = o match {
      case that: Key[_] => this.task == that.task && this.enclosing == that.enclosing
      case _            => false
    }
    override def hashCode(): Int = (task.## * 31) ^ enclosing.##
    def cacheKey: AnyTaskKey = {
      if (task == enclosing) task.asInstanceOf[ScopedKey[Task[Any]]]
      else {
        val am = enclosing.scope.extra match {
          case Select(a) => a.put(scopedKeyAttribute, task.asInstanceOf[AnyTaskKey])
          case _ => AttributeMap.empty.put(scopedKeyAttribute, task.asInstanceOf[AnyTaskKey])
        }
        Def.ScopedKey(enclosing.scope.copy(extra = Select(am)), enclosing.key)
      }
    }.asInstanceOf[AnyTaskKey]
  }
  private[sbt] object Key {
    def apply[T, U](key: ScopedKey[Task[T]], enclosing: ScopedKey[Task[U]]): Key[T] =
      new Key(key, enclosing.asInstanceOf[AnyTaskKey])
  }

  /** Records references to previous task value. This should be completely populated after settings finish loading. */
  private[sbt] final class References {
    private var map = IMap.empty[Key, Referenced]

    @deprecated("unused", "1.3.0")
    def recordReference[T](key: ScopedKey[Task[T]], format: JsonFormat[T]): Unit =
      recordReference(Key(key, key), format)
    // TODO: this arbitrarily chooses a JsonFormat.
    // The need to choose is a fundamental problem with this approach, but this should at least make a stable choice.
    def recordReference[T](key: Key[T], format: JsonFormat[T]): Unit = synchronized {
      map = map.put(key, new Referenced(key, format))
    }
    def getReferences: IMap[Key, Referenced] = synchronized { map }
  }

  /** Persists values of tasks t where there is some task referencing it via t.previous. */
  private[sbt] def complete(
      referenced: References,
      results: RMap[TaskId, Result],
      streams: Streams
  ): Unit = {
    val map = referenced.getReferences
    val reverse = map.keys.groupBy(_.task)

    // We first collect all of the successful tasks and write their scoped key into a map
    // along with their values.
    val successfulTaskResults = (
      for
        case results.TPair(task: Task[?], Result.Value(v)) <- results.toTypedSeq
        key <- task.get(Def.taskDefinitionKey).asInstanceOf[Option[AnyTaskKey]]
      yield key -> v
    ).toMap
    // We then traverse the successful results and look up all of the referenced values for
    // each of these tasks. This can be a many to one relationship if multiple tasks refer
    // the previous value of another task. For each reference we find, we check if the task has
    // been successfully evaluated. If so, we write it to the appropriate previous cache for
    // the completed task.
    for {
      (k, v) <- successfulTaskResults
      keys <- reverse.get(k)
      key <- keys if successfulTaskResults.contains(key.enclosing)
      ref <- map.get(key.asInstanceOf[Key[Any]])
    } {
      val out = streams(key.cacheKey).cacheStoreFactory.make(StreamName)
      try out.write(v)(using ref.stamped)
      catch { case NonFatal(_) => }
    }
  }
  private[sbt] val scopedKeyAttribute = AttributeKey[AnyTaskKey](
    "previous-scoped-key-attribute",
    "Specifies a scoped key for a task on which .previous is called. Used to " +
      "set the cache directory for the task-specific previous value: see Previous.runtimeInEnclosingTask."
  )

  /** Public as a macro implementation detail.  Do not call directly. */
  def runtime[T](skey: TaskKey[T])(using format: JsonFormat[T]): Initialize[Task[Option[T]]] = {
    type Inputs = (Task[Previous], ScopedKey[Task[T]], References)
    val inputs = (Global / cache, Def.validated(skey, selfRefOk = true), Global / references)
    Def.app[Inputs, Task[Option[T]]](inputs) { case (prevTask, resolved, refs) =>
      val key = Key(resolved, resolved)
      refs.recordReference(key, format) // always evaluated on project load
      prevTask.map(_.get(key)) // evaluated if this task is evaluated
    }
  }

  /** Public as a macro implementation detail.  Do not call directly. */
  def runtimeInEnclosingTask[T](skey: TaskKey[T])(using
      format: JsonFormat[T]
  ): Initialize[Task[Option[T]]] = {
    type Inputs = (Task[Previous], ScopedKey[Task[T]], References, ScopedKey[?])
    val inputs = (
      Global / cache,
      Def.validated(skey, selfRefOk = true),
      Global / references,
      Def.resolvedScoped
    )
    Def.app[Inputs, Task[Option[T]]](inputs) { case (prevTask, resolved, refs, inTask) =>
      val key = Key(resolved, inTask.asInstanceOf[ScopedKey[Task[Any]]])
      refs.recordReference(key, format) // always evaluated on project load
      prevTask.map(_.get(key))
    }
  }
}
