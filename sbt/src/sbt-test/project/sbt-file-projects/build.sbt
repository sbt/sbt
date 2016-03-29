val a = "a"
val f = file("a")
val g = taskKey[Unit]("A task in the root project")

val p = Project(a, f).
  settingSets(AddSettings.autoPlugins, AddSettings.sbtFiles( file("a.sbt") ))

val b = (project in file("b"))

g := println("Hello.")
