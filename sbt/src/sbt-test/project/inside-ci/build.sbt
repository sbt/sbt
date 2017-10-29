name := "inside-ci"

organization := "org.example"

val t = taskKey[Boolean]("inside-ci")

t := insideCI.value