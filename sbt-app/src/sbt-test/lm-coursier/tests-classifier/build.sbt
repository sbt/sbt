
val org = "io.get-coursier.tests"
val nme = "coursier-test-a"
val ver = "0.1-SNAPSHOT"

lazy val a = project
  .settings(
    organization := org,
    name := nme,
    Test / publishArtifact := true,
    version := ver,
    Compile / doc / sources := Seq.empty, // TODO fix doc task
    Test / doc / sources := Seq.empty
  )

lazy val b = project
  .settings(
    classpathTypes += "test-jar",
    libraryDependencies ++= Seq(
      org %% nme % ver,
      org %% nme % ver % "test" classifier "tests"
    )
  )

