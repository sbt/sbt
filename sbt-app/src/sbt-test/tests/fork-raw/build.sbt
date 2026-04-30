val munit = "org.scalameta" %% "munit" % "1.0.4"

scalaVersion := "3.8.2"
libraryDependencies += munit % Test

Test / fork := true
Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Raw
