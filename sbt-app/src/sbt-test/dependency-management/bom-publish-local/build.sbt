// BOM + publishLocal (sbt#4531): a uses BOM + jackson-core "*"; b depends on a.
// Verifies a's published ivy lists BOM-resolved jackson-core (forced), so b/update gets it.
// If b/update fails with jackson-core:*, the scripted subprocess may be using a cached lm-coursier.
ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"
ThisBuild / organization := "org.example"
ThisBuild / version := "1.0"
ThisBuild / scalaVersion := "2.12.18"

lazy val a = project
  .settings(common: _*)
  .settings(
    libraryDependencies += ("com.fasterxml.jackson" % "jackson-bom" % "2.21.0").pomOnly(),
    libraryDependencies += "com.fasterxml.jackson.core" % "jackson-core" % "*",
  )

lazy val b = project
  .settings(common: _*)
  .settings(
    libraryDependencies := Seq(organization.value %% "a" % version.value),
    TaskKey[Unit]("checkBomFromA") := {
      val report = (Compile / updateFull).value
      val compileConfig = report.configurations.find(_.configuration.name == "compile").getOrElse(
        sys.error("compile configuration not found")
      )
      val jacksonCore = compileConfig.modules.find(_.module.name == "jackson-core").getOrElse(
        sys.error("jackson-core not found in update report (expected from a's published ivy)")
      )
      val expected = "2.21.0"
      if (jacksonCore.module.revision != expected)
        sys.error(s"Expected jackson-core $expected from a's BOM-resolved ivy, got ${jacksonCore.module.revision}")
    },
  )

lazy val common = Seq(
  ivyPaths := IvyPaths(baseDirectory.value.toString, Some(((ThisBuild / baseDirectory).value / "ivy" / "cache").toString)),
)
