/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

val check = taskKey[Unit]("Repro for #4947: task at root when extraProjects creates auto root")

val a = project
val p = project
  .settings(
    name := "p",
    check := ()
  )
