import sbt.internal.AddSettings

val a = project
val b = project

val g = taskKey[Unit]("A task in the root project")
g := println("Hello.")
