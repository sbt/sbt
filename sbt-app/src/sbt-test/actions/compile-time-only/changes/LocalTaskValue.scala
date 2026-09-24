/*
 * sbt
 * Copyright 2026, Scala center
 * Licensed under Apache License 2.0 (see LICENSE)
 */

import sbt.*

object A:
  val task = Def.task {
    val local = taskKey[String]("")
    local.taskValue
    ()
  }
end A
