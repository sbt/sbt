/*
 * Copyright 2011, 2012 Johannes Rudolph
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package net.virtualvoid.sbt.graph

import sbt._
import Keys._

object Plugin extends sbt.Plugin {
  val dependencyGraphMLFile = SettingKey[File]("dependency-graph-ml-file",
    "The location the graphml file should be generated at")
  val dependencyGraphML = TaskKey[File]("dependency-graph-ml",
    "Creates a graphml file containing the dependency-graph for a project")
  val asciiGraph = TaskKey[String]("dependency-graph-string",
    "Returns a string containing the ascii representation of the dependency graph for a project")
  val dependencyGraph = TaskKey[Unit]("dependency-graph",
    "Prints the ascii graph to the console")
  val asciiTree = TaskKey[String]("dependency-tree-string",
    "Returns a string containing an ascii tree representation of the dependency graph for a project")
  val dependencyTree = TaskKey[Unit]("dependency-tree",
    "Prints the ascii tree to the console")
  val ivyReportFunction = TaskKey[String => File]("ivy-report-function",
    "A function which returns the file containing the ivy report from the ivy cache for a given configuration")
  val ivyReport = TaskKey[File]("ivy-report",
    "A task which returns the location of the ivy report file for a given configuration (default `compile`).")

  def graphSettings = seq(
    ivyReportFunction <<= (projectID, ivyModule, appConfiguration) map { (projectID, ivyModule, config) =>
      val home = config.provider.scalaProvider.launcher.ivyHome
      (c: String) => file("%s/cache/%s-%s-%s.xml" format (home, projectID.organization, crossName(ivyModule), c))
    }
  ) ++ Seq(Compile, Test, Runtime, Provided, Optional).flatMap(ivyReportForConfig)

  def ivyReportForConfig(config: Configuration) = inConfig(config)(seq(
    ivyReport <<= ivyReportFunction map (_(config.toString)) dependsOn(update),
    asciiGraph <<= asciiGraphTask,
    dependencyGraph <<= printAsciiGraphTask,
    asciiTree <<= asciiTreeTask,
    dependencyTree <<= printAsciiTreeTask,
    dependencyGraphMLFile <<= target / "dependencies-%s.graphml".format(config.toString),
    dependencyGraphML <<= dependencyGraphMLTask
  ))

  def asciiGraphTask = (ivyReport) map { report =>
    IvyGraphMLDependencies.asciiGraph(report.getAbsolutePath)
  }

  def printAsciiGraphTask =
    (streams, asciiGraph) map (_.log.info(_))

  def dependencyGraphMLTask =
    (ivyReport, dependencyGraphMLFile, streams) map { (report, resultFile, streams) =>
      IvyGraphMLDependencies.transform(report.getAbsolutePath, resultFile.getAbsolutePath)
      streams.log.info("Wrote dependency graph to '%s'" format resultFile)
      resultFile
    }

  def asciiTreeTask = (ivyReport) map { report =>
    IvyGraphMLDependencies.asciiTree(report.getAbsolutePath)
  }

  def printAsciiTreeTask =
    (streams, asciiTree) map (_.log.info(_))
  
  def crossName(ivyModule: IvySbt#Module) =
    ivyModule.moduleSettings match {
      case ic: InlineConfiguration => ic.module.name
      case _ =>
        throw new IllegalStateException("sbt-dependency-graph plugin currently only supports InlineConfiguration of ivy settings (the default in sbt)")
    }
}