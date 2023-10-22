scalaVersion := "2.12.16"
autoScalaLibrary := false
libraryDependencies += "com.chuusai" % "shapeless_2.12" % "2.3.2"

val checkScalaLibrary = TaskKey[Unit]("checkScalaLibrary")

checkScalaLibrary := {
  val scalaLibsJars = (Compile / managedClasspath)
    .value
    .map(_.data.getName)
    .filter(_.startsWith("scala-library"))
    .sorted
  val expectedScalaLibsJars = Seq(
    "scala-library-2.12.0.jar"
  )
  assert(
    scalaLibsJars == expectedScalaLibsJars,
    s"$scalaLibsJars != $expectedScalaLibsJars"
  )
}
