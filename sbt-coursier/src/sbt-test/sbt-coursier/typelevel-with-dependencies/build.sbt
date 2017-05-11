scalaVersion := "2.12.2-bin-typelevel-4"
scalaOrganization := "org.typelevel"
scalacOptions += "-Yinduction-heuristics"

libraryDependencies ++= Seq(
  "com.47deg" %% "freestyle" % "0.1.0",
  // CrossVersion.patch not available in sbt 0.13.8
  compilerPlugin("org.scalamacros" % "paradise_2.12.2" % "2.1.0")
)
