val commonSettings = Seq(
  libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1" % Test
)

lazy val root = (project in file(".")).
  aggregate(sub1, sub2).
  settings(inThisBuild(List(
      organization := "com.example",
      version := "0.0.1-SNAPSHOT",
      scalaVersion := "2.10.6"
    )),
    commonSettings
  )

lazy val rootRef = LocalProject("root")

lazy val sub1 = project.
  dependsOn(rootRef).
  settings(
    commonSettings
  )

lazy val sub2 = project.
  dependsOn(rootRef).
  settings(
    commonSettings
  )
