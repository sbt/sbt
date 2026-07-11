ThisBuild / scalaVersion := "2.13.18"
ThisBuild / usePipelining := true

lazy val root = (project in file("."))
  .aggregate(upstream, `downstream-transitive`, `downstream-direct`, `downstream-plusone`)
  .settings(
    name := "pipelining Java then Scala",
  )

lazy val upstream = project

lazy val `downstream-transitive` = project
  .in(file("downstream-transitive"))
  .dependsOn(upstream)

lazy val `downstream-direct` = project
  .in(file("downstream-direct"))
  .dependsOn(upstream)
  .settings(
    dependencyMode := DependencyMode.Direct,
  )

lazy val `downstream-plusone` = project
  .in(file("downstream-plusone"))
  .dependsOn(upstream)
  .settings(
    dependencyMode := DependencyMode.PlusOne,
  )
