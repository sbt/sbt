val a = "a"
val f = file("a")
val g = taskKey[Unit]("A task in the root project")

val p = Project(a, f).autoSettings(AddSettings.sbtFiles( file("a.sbt") ))

val b = Project("b", file("b"))

g := println("Hello.")
