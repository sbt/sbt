
val org = "io.get-coursier.tests"
val nme = "coursier-test-a"
val ver = "0.1-SNAPSHOT"

lazy val a = project
  .settings(
    organization := org,
    name := nme,
    publishArtifact.in(Test) := true,
    version := ver
  )

lazy val b = project
  .settings(
    classpathTypes += "test-jar",
    libraryDependencies ++= Seq(
      org %% nme % ver,
      org %% nme % ver % "test" classifier "tests"
    )
  )

