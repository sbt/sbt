lazy val check = taskKey[Unit]("Runs the check")

def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    ivyPaths := new IvyPaths( (baseDirectory in ThisBuild).value, Some((target in LocalRootProject).value / "ivy-cache")),
    libraryDependencies := Seq("net.sf.json-lib" % "json-lib" % "2.4" classifier "jdk15" intransitive()),
    autoScalaLibrary := false, // avoid downloading fresh scala-library/scala-compiler
    managedScalaInstance := false,
    updateOptions := updateOptions.value.withConsolidatedResolution(true)
  )
lazy val root = (project in file(".")).settings(
  organization in ThisBuild := "org.example",
  version in ThisBuild := "1.0"
)

lazy val a = project.
  settings(commonSettings: _*).
  settings(
    artifact in (Compile, packageBin) := Artifact("demo")
  )

lazy val b = project.
  settings(commonSettings: _*).
  settings(
    check := {
      val report = update.value
      val configurationReport = (report.configurations find {_.configuration == "compile"}).head
      val x = configurationReport.modules match {
        case Seq(moduleReport) =>
          moduleReport.module match {
            case ModuleID("net.sf.json-lib", "json-lib", "2.4", _, _, _, _, _, _, _, _) => ()
            case x => sys.error("Unexpected module: " + x.toString)
          }
        case x => sys.error("Unexpected modules: "  + x.toString)
      }
    }
  )

lazy val c = project.
  settings(commonSettings: _*).
  settings(
    libraryDependencies := Seq(organization.value %% "a" % version.value)
  )
