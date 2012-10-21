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
import complete.Parser

import org.apache.ivy.core.resolve.ResolveOptions

object Plugin extends sbt.Plugin {
  val dependencyGraphMLFile = SettingKey[File]("dependency-graph-ml-file",
    "The location the graphml file should be generated at")
  val dependencyGraphML = TaskKey[File]("dependency-graph-ml",
    "Creates a graphml file containing the dependency-graph for a project")
  val moduleGraph = TaskKey[IvyGraphMLDependencies.ModuleGraph]("module-graph",
    "The dependency graph for a project")
  val asciiGraph = TaskKey[String]("dependency-graph-string",
    "Returns a string containing the ascii representation of the dependency graph for a project")
  val dependencyGraph = InputKey[Unit]("dependency-graph",
    "Prints the ascii graph to the console")
  val asciiTree = TaskKey[String]("dependency-tree-string",
    "Returns a string containing an ascii tree representation of the dependency graph for a project")
  val dependencyTree = TaskKey[Unit]("dependency-tree",
    "Prints the ascii tree to the console")
  val ivyReportFunction = TaskKey[String => File]("ivy-report-function",
    "A function which returns the file containing the ivy report from the ivy cache for a given configuration")
  val ivyReport = TaskKey[File]("ivy-report",
    "A task which returns the location of the ivy report file for a given configuration (default `compile`).")
  val ignoreMissingUpdate = TaskKey[UpdateReport]("update-ignore-missing",
    "A copy of the update task which ignores missing artifacts")

  def graphSettings = seq(
    ivyReportFunction <<= (sbtVersion, target, projectID, ivyModule, appConfiguration, streams) map { (sbtV, target, projectID, ivyModule, config, streams) =>
      sbtV match {
        case Version(0, min, fix) if min > 12 || (min == 12 && fix >= 1) =>
          ivyModule.withModule(streams.log) { (i, moduleDesc, _) =>
            val id = ResolveOptions.getDefaultResolveId(moduleDesc)
            (c: String) => file("%s/resolution-cache/reports/%s/%s-resolved.xml" format (target, id,c))
          }
        case _ =>
          val home = config.provider.scalaProvider.launcher.ivyHome
          (c: String) => file("%s/cache/%s-%s-%s.xml" format (home, projectID.organization, crossName(ivyModule), c))
      }
    }
  ) ++ Seq(Compile, Test, Runtime, Provided, Optional).flatMap(ivyReportForConfig)

  def ivyReportForConfig(config: Configuration) = inConfig(config)(seq(
    ivyReport <<= ivyReportFunction map (_(config.toString)) dependsOn(ignoreMissingUpdate),
    moduleGraph <<= ivyReport map (absoluteReportPath.andThen(IvyGraphMLDependencies.graph)),
    asciiGraph <<= moduleGraph map IvyGraphMLDependencies.asciiGraph,
    dependencyGraph <<= InputTask(parser) { force =>
      (force, moduleGraph, streams) map  { (force, graph, streams) =>
        if (force || graph.nodes.size < 15) {
          streams.log.info(IvyGraphMLDependencies.asciiGraph(graph))
        } else {
          streams.log.info(IvyGraphMLDependencies.asciiTree(graph))

          if (!force) {
            streams.log.info("\n")
            streams.log.info("Note: The graph was estimated to be too big to display (> 15 nodes). Use `dependency-graph --force` to force graph display.")
          }
        }
      }
    },
    asciiTree <<= moduleGraph map IvyGraphMLDependencies.asciiTree,
    dependencyTree <<= print(asciiTree),
    dependencyGraphMLFile <<= target / "dependencies-%s.graphml".format(config.toString),
    dependencyGraphML <<= dependencyGraphMLTask,
    Compat.ignoreMissingUpdateT
  ))

  def printAsciiGraphTask =
    (streams, asciiGraph) map (_.log.info(_))

  def dependencyGraphMLTask =
    (moduleGraph, dependencyGraphMLFile, streams) map { (graph, resultFile, streams) =>
      IvyGraphMLDependencies.saveAsGraphML(graph, resultFile.getAbsolutePath)
      streams.log.info("Wrote dependency graph to '%s'" format resultFile)
      resultFile
    }

  def absoluteReportPath = (file: File) => file.getAbsolutePath

  def print(key: TaskKey[String]) =
    (streams, key) map (_.log.info(_))

  import Project._
  val parser: State => Parser[Boolean] = { (state: State) =>
    import complete.DefaultParsers._

    (Space ~> token("--force")).?.map(_.isDefined)
  }

  def crossName(ivyModule: IvySbt#Module) =
    ivyModule.moduleSettings match {
      case ic: InlineConfiguration => ic.module.name
      case _ =>
        throw new IllegalStateException("sbt-dependency-graph plugin currently only supports InlineConfiguration of ivy settings (the default in sbt)")
    }

  val VersionPattern = """(\d+)\.(\d+)\.(\d+)(?:-(.*))?""".r
  object Version {
    def unapplySeq(str: String): Option[(Int, Int, Int, Seq[String])] = str match {
      case VersionPattern(major, minor, fix, appendix) => Some((major.toInt, minor.toInt, fix.toInt, Option(appendix).toSeq))
      case _ => None
    }
  }
}