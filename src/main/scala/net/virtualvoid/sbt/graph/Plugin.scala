package net.virtualvoid.sbt.graph

import sbt._
import Keys._

object Plugin extends sbt.Plugin {
	val dependencyGraphTask = TaskKey[File]("dependency-graph")

	def graphSettings = Seq(
    dependencyGraphTask <<= (projectID, appConfiguration, target, streams) map { (projectID, config, target, streams) =>
      val home = config.provider.scalaProvider.launcher.ivyHome
      val fileName = "%s/cache/%s-%s-compile.xml" format (home, projectID.organization, projectID.name)

      val resultFile = target / "dependencies.graphml"
      IvyGraphMLDependencies.transform(fileName, resultFile.getAbsolutePath)
      streams.log.info("Wrote dependency graph to '%s'" format resultFile)
      resultFile
    } dependsOn(deliverLocal)
  )
}