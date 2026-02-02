// BOM + publishLocal (sbt#4531): a uses BOM + jackson-core "*"; b depends on a.
// For publishLocal, a's published ivy may still list jackson-core:*; so b also needs the BOM
// to resolve that transitive * (per eed3si9n: BOM needs to be added to all subprojects).
// Use `common,` not `common*`—compiler's vararg hint is misleading; sbt accepts Seq here.
ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"
ThisBuild / organization := "org.example"
ThisBuild / version := "1.0"
ThisBuild / scalaVersion := "2.12.18"

lazy val a = project
  .settings(
    common,
    libraryDependencies += ("com.fasterxml.jackson" % "jackson-bom" % "2.21.0").pomOnly(),
    libraryDependencies += "com.fasterxml.jackson.core" % "jackson-core" % "*",
  )

lazy val b = project
  .settings(
    common,
    libraryDependencies += ("com.fasterxml.jackson" % "jackson-bom" % "2.21.0").pomOnly(),
    libraryDependencies += organization.value %% "a" % version.value,
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
