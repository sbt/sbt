ThisBuild / scalaVersion := "3.3.4"

val c = taskKey[Unit]("A custom task named 'c'")
c := { println("Custom task 'c' executed") }
