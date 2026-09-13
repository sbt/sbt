val munit = "org.scalameta" %% "munit" % "1.0.4"

scalaVersion := "3.9.0"
libraryDependencies += munit % Test

Test / fork := true
Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Raw
