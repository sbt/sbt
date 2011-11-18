package net.virtualvoid.sbt.graph

import sbt._
import Keys._

object Plugin extends sbt.Plugin {
  val dependencyGraphTask = TaskKey[File]("dependency-graph")

  def graphSettings = Seq(
    dependencyGraphTask <<= (projectID, scalaVersion, appConfiguration, target, streams) map { (projectID, scalaVersion, config, target, streams) =>
      val home = config.provider.scalaProvider.launcher.ivyHome

      val fileName = "%s/cache/%s-%s-compile.xml" format (home, projectID.organization, crossName(projectID, scalaVersion))

      val resultFile = target / "dependencies.graphml"
      IvyGraphMLDependencies.transform(fileName, resultFile.getAbsolutePath)
      streams.log.info("Wrote dependency graph to '%s'" format resultFile)
      resultFile
    } dependsOn(deliverLocal)
  )

  def crossName(moduleId: ModuleID, scalaVersion: String) =
    moduleId.name + (
      if (moduleId.crossVersion)
        "_"+scalaVersion
      else
        ""
    )
}