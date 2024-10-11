val unpackage = TaskKey[Unit]("unpackage")

ThisBuild / scalaVersion := "2.12.20"

lazy val root = (project in file("."))
  .settings(
    forConfig(Compile, "src"),
    forConfig(Test, "test-src"),
    libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.14.0" % Test,
    unmanagedSources / includeFilter := "*.java" | "*.scala"
  )

def forConfig(conf: Configuration, name: String) = Project.inConfig(conf)( unpackageSettings(name) )

def unpackageSettings(name: String) = Seq(
  unmanagedSourceDirectories := (baseDirectory.value / name) :: Nil,
  unmanagedResources / excludeFilter := (unmanagedSources / includeFilter).value,
  unmanagedResourceDirectories := unmanagedSourceDirectories.value,
  unpackage := {
    IO.unzip((packageSrc / artifactPath).value, baseDirectory.value / name)
    IO.delete(baseDirectory.value / name / "META-INF")
  }
)
