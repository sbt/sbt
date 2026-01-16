scalaVersion := "2.13.16"
semanticdbEnabled := true
semanticdbIncludeInJar := true

// see https://github.com/sbt/sbt/issues/5886
lazy val check = taskKey[Unit]("Checks that scalacOptions have the same number of parameters across configurations")
lazy val anyConfigInThisProject = ScopeFilter(configurations = inAnyConfiguration)

lazy val Custom = config("custom").extend(Compile)

lazy val root = (project in file("."))
  .configs(Custom)
  .settings(
    inConfig(Custom)(Defaults.configSettings ++ sbt.plugins.SemanticdbPlugin.configurationSettings),
    check := {
      val scalacOptionsCountsAcrossConfigs = scalacOptions.?.all(anyConfigInThisProject)
        .value
        .map(_.toSeq.flatten.size)
        .filterNot(_ == 0)
        .distinct
      assert(
        scalacOptionsCountsAcrossConfigs.size == 1,
        s"Configurations expected to have the same number of scalacOptions but found different numbers: $scalacOptionsCountsAcrossConfigs"
      )

      val converter = fileConverter.value
      val p = converter.toPath((Compile / packageBin).value)
      IO.unzip(p.toFile(), target.value / "extracted")

      val testp = converter.toPath((Test / packageBin).value)
      IO.unzip(testp.toFile(), target.value / "test-extracted")
    }
  )
