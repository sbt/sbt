Global / localCacheDirectory := baseDirectory.value / "diskcache"

scalaVersion := "3.8.4"

val munit = "org.scalameta" %% "munit" % "1.0.4"

lazy val foo = project

lazy val app = project
  .dependsOn(foo)
  .settings(
    libraryDependencies += munit % Test
  )

lazy val root = (project in file("."))
  .aggregate(foo, app)
