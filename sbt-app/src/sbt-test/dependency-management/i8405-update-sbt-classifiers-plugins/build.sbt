ivyConfiguration := Def.uncached {
  throw new RuntimeException("updateSbtClassifiers should use updateSbtClassifiers / ivyConfiguration")
}

dependencyResolution := Def.uncached {
  throw new RuntimeException("updateSbtClassifiers should use updateSbtClassifiers / dependencyResolution")
}

lazy val root = (project in file("."))
  .settings(
    scalaVersion := "2.11.12",
    scalaOrganization := "doesnt.exist",
    name := "myProjectName",

    TaskKey[Unit]("checkPluginsInUpdateSbtClassifiers") := Def.uncached {
      val updateReport = updateSbtClassifiers.value
      val moduleReports = updateReport.configurations.find(_.configuration.name == "default").get.modules

      // Calling "distinct" as there are different entries for sources and javadoc classifiers with same module
      val moduleIds = moduleReports.map(_.module).distinct
      val moduleIdsShort = moduleIds.map(m => s"${m.organization}:${m.name}")

      // Verify that the target plugin sbt-buildinfo is included in the output
      // The plugin may be cross-versioned as sbt-buildinfo_sbt2_3, etc.
      val hasBuildinfoPlugin = moduleIdsShort.exists(id => id.startsWith("com.eed3si9n:sbt-buildinfo"))
      assert(
        hasBuildinfoPlugin,
        s"Plugin com.eed3si9n:sbt-buildinfo was not found in updateSbtClassifiers output. Found modules: ${moduleIdsShort.sorted.mkString(", ")}"
      )
    }
  )

