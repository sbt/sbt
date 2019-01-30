/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import sbt.Keys.state
import sbt._

private[sbt] object TaskRepository {
  private[sbt] type Repr = MutableRepository[TaskKey[_], Any]
  private[sbt] def proxy[T: Manifest](taskKey: TaskKey[T], task: => T): Def.Setting[Task[T]] =
    proxy(taskKey, Def.task(task))
  private[sbt] def proxy[T: Manifest](
      taskKey: TaskKey[T],
      task: Def.Initialize[Task[T]]
  ): Def.Setting[Task[T]] =
    taskKey := Def.taskDyn {
      val taskRepository = state.value
        .get(Keys.taskRepository)
        .getOrElse {
          val msg = "TaskRepository.proxy called before state was initialized"
          throw new IllegalStateException(msg)
        }
      taskRepository.get(taskKey) match {
        case Some(value: T) => Def.task(value)
        case _ =>
          Def.task {
            val value = task.value
            taskRepository.put(taskKey, value)
            value
          }
      }
    }.value
}
