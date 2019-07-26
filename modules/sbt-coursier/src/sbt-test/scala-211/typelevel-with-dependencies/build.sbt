
scalaVersion := "2.12.2-bin-typelevel-4"
scalaOrganization := "org.typelevel"
scalacOptions += "-Yinduction-heuristics"

libraryDependencies ++= Seq(
  "com.47deg" %% "freestyle" % "0.1.0",
  compilerPlugin("org.scalamacros" %% "paradise" % "2.1.1" cross CrossVersion.patch)
)
