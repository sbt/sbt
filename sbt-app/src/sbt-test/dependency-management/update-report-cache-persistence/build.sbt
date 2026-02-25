ThisBuild / scalaVersion := "2.13.16"

lazy val checkCacheBehavior = taskKey[Unit]("Validates update cache miss then hit")

lazy val root = (project in file("."))
  .settings(
    name := "update-report-cache-persistence-test",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.18" % Test,
    checkCacheBehavior := {
      val marker = target.value / "update-cache-checked.marker"
      val report = update.value
      if (marker.exists) {
        require(
          report.stats.cached,
          s"Expected cached update report on second run, got stats=${report.stats}"
        )
      } else {
        require(
          !report.stats.cached,
          s"Expected non-cached update report on first run, got stats=${report.stats}"
        )
        IO.touch(marker)
      }
    }
  )
