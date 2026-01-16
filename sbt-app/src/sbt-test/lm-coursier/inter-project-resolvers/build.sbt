
lazy val a = project
  .settings(sharedSettings)
  .settings(
    resolvers += "Jitpack Repo" at "https://jitpack.io"
  )

lazy val b = project
  .dependsOn(a)
  .settings(sharedSettings)
  .settings(
    // resolver added in inter-project dependency only - should still be fine
    libraryDependencies += "com.github.jupyter" % "jvm-repr" % "0.3.0"
  )

lazy val root = project
  .in(file("."))
  .aggregate(a, b)
  .settings(sharedSettings)


lazy val sharedSettings = Seq(
  scalaVersion := "2.12.8"
)
