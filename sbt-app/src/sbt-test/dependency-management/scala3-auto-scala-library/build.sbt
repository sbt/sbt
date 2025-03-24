autoScalaLibrary := false
scalaVersion := "3.3.4"
libraryDependencies += "com.chuusai" % "shapeless_2.13" % "2.3.3"

val checkScalaLibrary = TaskKey[Unit]("checkScalaLibrary")

checkScalaLibrary := {
  val scalaLibsJars = (Compile / managedClasspath).value
    .map(_.data.name)
    .filter(name => name.startsWith("scala-library") || name.startsWith("scala3-library"))
    .sorted
  val expectedScalaLibsJars = Seq(
    "scala-library-2.13.0.jar"
  )
  assert(
    scalaLibsJars == expectedScalaLibsJars,
    s"$scalaLibsJars != $expectedScalaLibsJars"
  )
}
