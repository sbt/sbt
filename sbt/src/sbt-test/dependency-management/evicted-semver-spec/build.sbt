ThisBuild / organization := "com.example"
ThisBuild / scalaVersion := "2.13.3"
ThisBuild / versionScheme := Some("semver-spec")
ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

def commonSettings: Seq[Def.Setting[_]] =
  Seq(
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
    },
  )

val use2 = project
  .settings(commonSettings)
  .settings(
    name := "use2",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-blaze-server" % "0.21.11",
      // https://repo1.maven.org/maven2/org/typelevel/cats-effect_2.13/3.0.0-M4/cats-effect_2.13-3.0.0-M4.pom
      // is published with early-semver
      "org.typelevel" %% "cats-effect" % "3.0.0-M4",
    ),
  )
