// ThisBuild / useCoursier := false
ThisBuild / organization := "com.example"
ThisBuild / scalaVersion := "2.12.12"
ThisBuild / versionScheme := Some("semver-spec")
ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    ivyPaths := IvyPaths(
      (ThisBuild / baseDirectory).value,
      Some((LocalRootProject / target).value / "ivy-cache")
    ),
    fullResolvers := fullResolvers.value.filterNot(_.name == "inter-project"),
    publishTo := Some(MavenCache("local-maven", (LocalRootProject / target).value / "local-maven")),
    resolvers += MavenCache("local-maven", (LocalRootProject / target).value / "local-maven"),
  )

lazy val root = (project in file("."))
  .settings(commonSettings)

val `v1-0-0` = (project in file("v1.0.0"))
  .settings(commonSettings)
  .settings(
    name := "semver-spec-test",
    version := "1.0.0",
  )

val `v1-1-0` = (project in file("v1.1.0"))
  .settings(commonSettings)
  .settings(
    name := "semver-spec-test",
    version := "1.1.0",
  )

val middle = project
  .settings(commonSettings)
  .settings(
    name := "middle",
    version := "1.0.0",
    libraryDependencies += "com.example" %% "semver-spec-test" % "1.0.0",
  )

val use = project
  .settings(commonSettings)
  .settings(
    name := "use",
    libraryDependencies ++= Seq(
      "com.example" %% "semver-spec-test" % "1.1.0",
      "com.example" %% "middle" % "1.0.0",
    ),
    TaskKey[Unit]("check") := {
      val report = updateFull.value
      val log = streams.value.log
      val extraAttributes = (report.allModules flatMap { _.extraAttributes}) collect {
          case ("info.versionScheme", v) => v
        }
      log.info(s"extraAttributes = $extraAttributes")
      assert(extraAttributes.nonEmpty, s"$extraAttributes is empty")
      val ew = EvictionWarning(ivyModule.value, (evicted / evictionWarningOptions).value, report)
      assert(ew.directEvictions.isEmpty, s"${ew.directEvictions} is not empty")
    },
  )
