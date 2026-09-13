/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

@transient
val check4947 = taskKey[Unit]("")

@transient
val check5661 = taskKey[Unit]("")

@transient
val check9493 = taskKey[Unit]("")

organization := "com.example"

val a = project
val p = project
  .settings(
    name := "p",
    // subproject-level task
    check4947 := {},

    check9493 := {
      val o = organization.value
      assert(o == "com.example", s"actual: $o")
    }
  )

LocalProject("mc") / cantTouchThis := "foo"
LocalRootProject / check5661 := {
  val actual = (LocalProject("mc") / cantTouchThis).value
  assert(actual == "foo", s"actual: $actual")
}
