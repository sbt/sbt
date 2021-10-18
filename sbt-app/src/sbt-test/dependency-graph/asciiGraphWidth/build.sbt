ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.5"

name := "asciiGraphWidthSpecs"

lazy val whenIsDefault = (project in file("when-is-default"))
  .settings(
    libraryDependencies += "org.typelevel" %% "cats-effect" % "3.1.0",
    check := checkTask.value
  )
lazy val whenIs20 = (project in file("when-is-20"))
  .settings(
    asciiGraphWidth := 20,
    libraryDependencies += "org.typelevel" %% "cats-effect" % "3.1.0",
    check := checkTask.value
  )

lazy val check = taskKey[Unit]("check")
lazy val checkTask = Def.task {
  val context = thisProject.value
  val expected = IO.read(file(s"${context.base}/expected.txt"))
  val actual = (Compile / dependencyTree / asString).value
  require(actual == expected, s"${context.id} is failed.")
}
