/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.bsp

object BuildTargetName {
  def fromScope(project: String, config: String): String = {
    config match {
      case "compile" => project
      case _         => s"$project-$config"
    }
  }
}
