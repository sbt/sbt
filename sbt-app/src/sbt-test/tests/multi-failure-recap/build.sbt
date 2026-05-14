ThisBuild / scalaVersion := "2.13.16"

def junit = libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % Test

lazy val a = project.settings(junit)
lazy val b = project.settings(junit)
lazy val c = project.settings(junit)

lazy val root = (project in file("."))
  .aggregate(a, b, c)
  .settings(
    commands ++= Seq(
      sbt.multifailurerecap.Checks.verifyRecap,
      sbt.multifailurerecap.Checks.verifyNoRecap,
    )
  )
