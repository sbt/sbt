lazy val root = (project in file(".")).
  settings(
    forConfig(Compile, "src"),
    forConfig(Test, "test-src"),
    baseSettings
  )

def baseSettings = Seq(
  scalaVersion := "2.11.8",
  libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.11.4" % Test,
  includeFilter in unmanagedSources := "*.java" | "*.scala"
)

def forConfig(conf: Configuration, name: String) = Project.inConfig(conf)( unpackageSettings(name) )

def unpackageSettings(name: String) = Seq(
  unmanagedSourceDirectories := (baseDirectory.value / name) :: Nil,
  excludeFilter in unmanagedResources := (includeFilter in unmanagedSources).value,
  unmanagedResourceDirectories := unmanagedSourceDirectories.value,
  unpackage := {
    IO.unzip(artifactPath in packageSrc value, baseDirectory.value / name)
    IO.delete(baseDirectory.value / name / "META-INF")
  }
)

val unpackage = TaskKey[Unit]("unpackage")
