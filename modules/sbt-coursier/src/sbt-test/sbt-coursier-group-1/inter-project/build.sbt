
lazy val sharedSettings = Seq(
  scalaVersion := "2.11.8"
)

/** Module with the same Maven coordinates as shapeless 2.3.1 */
lazy val `shapeless-mock` = project
  .settings(sharedSettings)
  .settings(
    organization := "com.chuusai",
    name := "shapeless",
    version := "2.3.1"
  )

lazy val a = project
  .settings(sharedSettings)
  .settings(
    organization := "com.pany",
    name := "a",
    version := "0.0.1"
  )

/** Transitively depends on the - real - shapeless 2.3.1 */
lazy val b = project
  .dependsOn(a)
  .settings(sharedSettings)
  .settings(
    organization := "com.pany",
    name := "b",
    version := "0.0.1",
    libraryDependencies += "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % "1.2.0-M1"
  )
