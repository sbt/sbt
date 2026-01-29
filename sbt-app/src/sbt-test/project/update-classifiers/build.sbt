lazy val a = project in file(".") dependsOn(b)
lazy val b = project
