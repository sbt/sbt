/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.Def.{ Initialize, ScopedKey }
import sbt.Previous._
import sbt.Scope.Global
import sbt.internal.util._
import sbt.std.TaskExtra._
import sbt.util.StampedFormat
import sjsonnew.JsonFormat

import scala.util.control.NonFatal

/**
 * Reads the previous value of tasks on-demand.  The read values are cached so that they are only read once per task execution.
 * `referenced` provides the `Format` to use for each key.
 */
private[sbt] final class Previous(streams: Streams, referenced: IMap[Previous.Keys, Referenced]) {
  private[this] val map = referenced.mapValues(toValue)
  private[this] def toValue = λ[Referenced ~> ReferencedValue](new ReferencedValue(_))

  private[this] final class ReferencedValue[T](referenced: Referenced[T]) {
    import referenced.{ task, inTask, stamped }
    lazy val previousValue: Option[T] = {
      try Option(
        streams(fullKey(task, inTask)).cacheStoreFactory.make(StreamName).read[T]()(stamped)
      )
      catch { case NonFatal(_) => None }
    }
  }

  /** Used by the .previous runtime implementation to get the previous value for task `key`. */
  private def get[T, U](key: ScopedKey[Task[T]], inTask: ScopedKey[Task[U]]): Option[T] =
    map.get(Previous.Keys(key, inTask)).flatMap(_.previousValue)
}
object Previous {
  import sjsonnew.BasicJsonProtocol.StringJsonFormat
  private[sbt] type ScopedTaskKey[T] = ScopedKey[Task[T]]
  private type AnyTaskKey = ScopedTaskKey[Any]
  private type Streams = sbt.std.Streams[ScopedKey[_]]

  /** The stream where the task value is persisted. */
  private final val StreamName = "previous"
  private[sbt] final val DependencyDirectory = "previous-dependencies"

  /** Represents a reference task.previous*/
  private[sbt] final class Referenced[T](
      val task: ScopedKey[Task[T]],
      val inTask: ScopedKey[Task[Any]],
      val format: JsonFormat[T]
  ) {
    def this(task: ScopedKey[Task[T]], format: JsonFormat[T]) =
      this(task, task.asInstanceOf[AnyTaskKey], format)
    lazy val stamped: JsonFormat[T] = StampedFormat.withStamp(task.key.manifest.toString)(format)
    def setTask(newTask: ScopedKey[Task[T]]) = new Referenced(newTask, format)
    override def toString: String = s"Referenced($task)"
    override def equals(o: Any): Boolean = o match {
      case that: Referenced[_] => this.task == that.task && this.inTask == that.inTask
      case _                   => false
    }
    override def hashCode(): Int = (task.## * 31) ^ inTask.##
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

  private[sbt] class Keys[T](val key: ScopedKey[Task[T]], val inTask: ScopedKey[Task[Any]]) {
    override def equals(o: Any): Boolean = o match {
      case that: Keys[_] => this.key == that.key && this.inTask == that.inTask
      case _             => false
    }
    override def hashCode(): Int = (key.## * 31) ^ inTask.##
  }
  private[sbt] object Keys {
    def apply[T, U](key: ScopedKey[Task[T]], inTask: ScopedKey[Task[U]]): Keys[T] =
      new Keys(key, inTask.asInstanceOf[AnyTaskKey])
  }

  /** Records references to previous task value. This should be completely populated after settings finish loading. */
  private[sbt] final class References {
    private[this] var map = IMap.empty[Keys, Referenced]
    type ReferencedSet[_] = Set[Referenced[_]]
    private[this] var reverse = IMap.empty[ScopedTaskKey, ReferencedSet]

    @deprecated("unused", "1.3.0")
    def recordReference[T](key: ScopedKey[Task[T]], format: JsonFormat[T]): Unit =
      recordReference(key, key, format)
    // TODO: this arbitrarily chooses a JsonFormat.
    // The need to choose is a fundamental problem with this approach, but this should at least make a stable choice.
    def recordReference[T, U](
        key: ScopedKey[Task[T]],
        inTask: ScopedKey[Task[U]],
        format: JsonFormat[T]
    ): Unit = synchronized {
      val referenced = new Referenced(key, inTask.asInstanceOf[AnyTaskKey], format)
      map = map.put(Keys(key, inTask), referenced)
      reverse.get(key) match {
        case Some(v) => reverse = reverse.put(key, v + referenced)
        case None    => reverse = reverse.put(key, Set(referenced))
      }
    }
    def getReferences: IMap[Keys, Referenced] = synchronized { map }
    private[sbt] def getReverse: IMap[ScopedTaskKey, ReferencedSet] = synchronized { reverse }
  }

  /** Persists values of tasks t where there is some task referencing it via t.previous. */
  private[sbt] def complete(
      referenced: References,
      results: RMap[Task, Result],
      streams: Streams
  ): Unit = {
    val map = referenced.getReferences
    val reverse = referenced.getReverse
    def impl[T, U](key: ScopedKey[Task[T]], inTask: ScopedKey[Task[U]], result: T): Unit =
      for (i <- map.get(Keys(key, inTask))) {
        val out = streams(fullKey(key, inTask)).cacheStoreFactory.make(StreamName)
        try out.write(result)(i.stamped)
        catch { case NonFatal(_) => }
      }

    var successes = IMap.empty[ScopedTaskKey, results.TPair]
    for {
      result @ results.TPair(Task(info, _), Value(_)) <- results.toTypedSeq
      key <- info.attributes get Def.taskDefinitionKey
    } {
      successes =
        successes.put(key.asInstanceOf[AnyTaskKey], result.asInstanceOf[results.TPair[Any]])
    }
    def process(s: IMap[ScopedTaskKey, results.TPair]): Unit = {
      s.toTypedSeq.foreach {
        case s.TPair(key, results.TPair(_, Value(v))) =>
          reverse.get(key.asInstanceOf[AnyTaskKey]) match {
            case Some(references) =>
              references.foreach { r =>
                val t = r.task.asInstanceOf[AnyTaskKey]
                if (s.contains(r.inTask.asInstanceOf[AnyTaskKey])) impl(t, r.inTask, v)
              }
            case _ =>
          }
      }
    }
    process(successes)
  }
  private[sbt] val scopedKeyAttribute = AttributeKey[AnyTaskKey](
    "previous-scoped-key-attribute",
    "Specifies a scoped key for a task on which .previous is called. Used to " +
      "set the cache directory for the task-specific previous value: see Previous.runtimeInThis."
  )

  private def fullKey[T, U](
      key: Def.ScopedKey[Task[T]],
      currentTask: Def.ScopedKey[Task[U]]
  ): Def.ScopedKey[Task[Any]] = {
    if (key == currentTask) key
    else {
      val am = currentTask.scope.extra match {
        case Select(a) => a.put(scopedKeyAttribute, key.asInstanceOf[AnyTaskKey])
        case _         => AttributeMap.empty.put(scopedKeyAttribute, key.asInstanceOf[AnyTaskKey])
      }
      Def.ScopedKey(currentTask.scope.copy(extra = Select(am)), currentTask.key)
    }
  }.asInstanceOf[AnyTaskKey]

  /** Public as a macro implementation detail.  Do not call directly. */
  def runtime[T](skey: TaskKey[T])(implicit format: JsonFormat[T]): Initialize[Task[Option[T]]] = {
    val inputs = (cache in Global) zip Def.validated(skey, selfRefOk = true) zip (references in Global)
    inputs {
      case ((prevTask, resolved), refs) =>
        refs.recordReference(resolved, resolved, format) // always evaluated on project load
        prevTask.map(_.get(resolved, resolved)) // evaluated if this task is evaluated
    }
  }

  /** Public as a macro implementation detail.  Do not call directly. */
  def runtimeInThis[T](skey: TaskKey[T])(
      implicit format: JsonFormat[T]
  ): Initialize[Task[Option[T]]] = {
    val inputs = (cache in Global)
      .zip(Def.validated(skey, selfRefOk = true))
      .zip(references in Global)
      .zip(Def.resolvedScoped)
    inputs {
      case (((prevTask, resolved), refs), inTask) =>
        val anyTaskKey = inTask.asInstanceOf[AnyTaskKey]
        refs.recordReference(resolved, anyTaskKey, format) // always evaluated on project load
        prevTask.map(_.get(resolved, anyTaskKey)) // evaluated if this task is evaluated
    }
  }
}
