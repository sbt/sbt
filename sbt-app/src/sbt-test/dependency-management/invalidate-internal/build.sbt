lazy val root = (project in file("."))
lazy val a = project.dependsOn(b)
lazy val b = project
