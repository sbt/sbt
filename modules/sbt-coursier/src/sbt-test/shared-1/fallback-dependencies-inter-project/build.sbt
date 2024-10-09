
lazy val a = project
  .settings(sharedSettings)
  .settings(
    libraryDependencies += "com.chuusai" %% "shapeless" % "2.3.234" from "https://oss.sonatype.org/content/repositories/releases/com/chuusai/shapeless_2.12/2.3.3/shapeless_2.12-2.3.3.jar"
  )

lazy val b = project
  .dependsOn(a)
  .settings(sharedSettings)

lazy val root = project
  .in(file("."))
  .aggregate(a, b)
  .settings(sharedSettings)


lazy val sharedSettings = Seq(
  scalaVersion := "2.12.8"
)
