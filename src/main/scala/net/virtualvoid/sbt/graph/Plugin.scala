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
  val dependencyGraphTask = TaskKey[File]("dependency-graph",
    "Creates a graphml file containing the dependency-graph for a project")
  val asciiGraph = TaskKey[String]("ascii-graph",
    "Returns a string containing the ascii representation of the dependency graph for a project")
  val printAsciiGraph = TaskKey[Unit]("print-ascii-graph",
    "Prints the ascii graph to the console")
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

    asciiGraph in Compile <<= asciiGraphTask(Compile),
    asciiGraph in Test <<= asciiGraphTask(Test),
    asciiGraph in Runtime <<= asciiGraphTask(Runtime),
    asciiGraph in Provided <<= asciiGraphTask(Provided),
    asciiGraph in Optional <<= asciiGraphTask(Optional),

    printAsciiGraph in Compile <<= printAsciiGraphTask(Compile),
    printAsciiGraph in Test <<= printAsciiGraphTask(Test),
    printAsciiGraph in Runtime <<= printAsciiGraphTask(Runtime),
    printAsciiGraph in Provided <<= printAsciiGraphTask(Provided),
    printAsciiGraph in Optional <<= printAsciiGraphTask(Optional),

    dependencyGraphTask <<= (ivyReportF, target, streams) map { (report, target, streams) =>
      val resultFile = target / "dependencies.graphml"
      IvyGraphMLDependencies.transform(report("compile").getAbsolutePath, resultFile.getAbsolutePath)
      streams.log.info("Wrote dependency graph to '%s'" format resultFile)
      resultFile
    } dependsOn(deliverLocal)
  )

  def asciiGraphTask(conf: Configuration) = (ivyReportF in conf) map { (report) =>
    IvyGraphMLDependencies.ascii(report(conf.name).getAbsolutePath)
  } dependsOn(deliverLocal)

  def printAsciiGraphTask(conf: Configuration) = (asciiGraph in conf, streams in conf) map { (graph, streams) =>
   streams.log.info(graph)
  }

  def crossName(moduleId: ModuleID, scalaVersion: String) =
    moduleId.name + (
      moduleId.crossVersion match {
        case CrossVersion.Disabled => ""
        case _ => "_"+scalaVersion
      }
    )
}