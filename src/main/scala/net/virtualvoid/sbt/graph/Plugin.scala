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
  val dependencyGraphML = TaskKey[File]("dependency-graph-ml",
    "Creates a graphml file containing the dependency-graph for a project")
  val asciiGraph = TaskKey[String]("dependency-graph-string",
    "Returns a string containing the ascii representation of the dependency graph for a project")
  val dependencyGraph = TaskKey[Unit]("dependency-graph",
    "Prints the ascii graph to the console")
  val ivyReportFunction = TaskKey[String => File]("ivy-report-function",
    "A function which returns the file containing the ivy report from the ivy cache for a given configuration")
  val ivyReport = InputKey[File]("ivy-report",
    "A task which returns the location of the ivy report file for a given configuration (default `compile`).")

  def graphSettings = Seq(
    ivyReportFunction <<= (projectID, ivyModule, appConfiguration) map { (projectID, ivyModule, config) =>
      val home = config.provider.scalaProvider.launcher.ivyHome
      (c: String) => file("%s/cache/%s-%s-%s.xml" format (home, projectID.organization, crossName(ivyModule), c))
    },
    ivyReport <<= inputTask { args =>
      (args, ivyReportFunction) map { (args, report) =>
        if (args.isEmpty)
          report("compile")
        else
          report(args(0))
      } dependsOn(update)
    },
    dependencyGraphML <<= (ivyReportFunction, target, streams) map { (report, target, streams) =>
      val resultFile = target / "dependencies.graphml"
      IvyGraphMLDependencies.transform(report("compile").getAbsolutePath, resultFile.getAbsolutePath)
      streams.log.info("Wrote dependency graph to '%s'" format resultFile)
      resultFile
    }
  ) ++ Seq(Compile, Test, Runtime, Provided, Optional).flatMap(asciiGraphSettings)

  def asciiGraphSettings(config: Configuration) =
    seq(
      asciiGraph in config <<= asciiGraphTask(config),
      dependencyGraph in config <<= printAsciiGraphTask(config)
    )

  def asciiGraphTask(conf: Configuration) = (ivyReportFunction in conf) map { (report) =>
    IvyGraphMLDependencies.ascii(report(conf.name).getAbsolutePath)
  } dependsOn(deliverLocal)

  def printAsciiGraphTask(conf: Configuration) = (streams in conf, asciiGraph in conf) map (_.log.info(_))
  def crossName(ivyModule: IvySbt#Module) =
    ivyModule.moduleSettings match {
      case ic: InlineConfiguration => ic.module.name
    }
}