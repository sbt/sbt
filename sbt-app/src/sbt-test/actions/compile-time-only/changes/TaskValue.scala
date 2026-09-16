/*
 * sbt
 * Copyright 2026, Scala center
 * Licensed under Apache License 2.0 (see LICENSE)
 */

import sbt.*

object A:
  def wrap(key: TaskKey[String]): Task[String] = key.taskValue
end A
