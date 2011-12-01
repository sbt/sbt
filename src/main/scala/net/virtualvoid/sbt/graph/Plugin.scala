package net.virtualvoid.sbt.graph

import sbt._
import Keys._

object Plugin extends sbt.Plugin {
  val dependencyGraphTask = TaskKey[File]("dependency-graph",
    "Creates a graphml file containing the dependency-graph for a project")
  val ivyReportF = SettingKey[String => File]("ivy-report-function",
    "A function which returns the file containing the ivy report from the ivy cache for a given configuration")
  val ivyReport = InputKey[File]("ivy-report",
    "A task which returns the location of the ivy report file for a given configuration (default `compile`).")

  def graphSettings = Seq(
    ivyReportF <<= (projectID, scalaVersion, appConfiguration) { (projectID, scalaVersion, config) =>
      val home = config.provider.scalaProvider.launcher.ivyHome
      (c: String) => file("%s/cache/%s-%s-%s.xml" format (home, projectID.organization, crossName(projectID, scalaVersion), c))
    },
    ivyReport <<= inputTask { args =>
      (args, ivyReportF) map { (args, report) =>
        if (args.isEmpty)
          report("compile")
        else
          report(args(0))
      }
    },
    dependencyGraphTask <<= (ivyReportF, target, streams) map { (report, target, streams) =>
      val resultFile = target / "dependencies.graphml"
      IvyGraphMLDependencies.transform(report("compile").getAbsolutePath, resultFile.getAbsolutePath)
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