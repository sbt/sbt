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
import net.virtualvoid.sbt.graph.IvyGraphMLDependencies.ModuleGraph

object Plugin extends sbt.Plugin {
  val dependencyGraphMLFile = SettingKey[File]("dependency-graph-ml-file",
    "The location the graphml file should be generated at")
  val dependencyGraphML = TaskKey[File]("dependency-graph-ml",
    "Creates a graphml file containing the dependency-graph for a project")
  val dependencyDotFile = SettingKey[File]("dependency-dot-file",
    "The location the dot file should be generated at")
  val dependencyDotNodeLabel = SettingKey[(String,String,String) => String]("dependency-dot-node-label",
    "Returns a formated string of a dependency. Takes organisation, name and version as parameters")
  val dependencyDotHead = SettingKey[String]("dependency-dot-head",
    "The head of the dot file. (e.g. to set your preferred node shapes)")
  val dependencyDot = TaskKey[File]("dependency-dot",
    "Creates a dot file containing the dpendency-graph for a project")
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
  val ignoreMissingUpdate = update in ivyReport
  val filterScalaLibrary = SettingKey[Boolean]("filter-scala-library",
    "Specifies if scala dependency should be filtered in dependency-* output"
  )

  val licenseInfo = TaskKey[Unit]("dependency-license-info",
    "Aggregates and shows information about the licenses of dependencies")

  // internal
  import ModuleGraphProtocol._
  val moduleGraphStore = TaskKey[IvyGraphMLDependencies.ModuleGraph]("module-graph-store", "The stored module-graph from the last run")

  val whatDependsOn = InputKey[Unit]("what-depends-on", "Shows information about what depends on the given module")

  def graphSettings = seq(
    ivyReportFunction <<= (sbtVersion, target, projectID, ivyModule, appConfiguration, streams) map { (sbtV, target, projectID, ivyModule, config, streams) =>
      sbtV match {
        case Version(0, min, fix, _) if min > 12 || (min == 12 && fix >= 1) =>
          ivyModule.withModule(streams.log) { (i, moduleDesc, _) =>
            val id = ResolveOptions.getDefaultResolveId(moduleDesc)
            (c: String) => file("%s/resolution-cache/reports/%s/%s-resolved.xml" format (target, id,c))
          }
        case _ =>
          val home = config.provider.scalaProvider.launcher.ivyHome
          (c: String) => file("%s/cache/%s-%s-%s.xml" format (home, projectID.organization, crossName(ivyModule), c))
      }
    },
    Compat.ignoreMissingUpdateT,
    filterScalaLibrary in Global := true
  ) ++ Seq(Compile, Test, Runtime, Provided, Optional).flatMap(ivyReportForConfig)

  def ivyReportForConfig(config: Configuration) = inConfig(config)(seq(
    ivyReport <<= ivyReportFunction map (_(config.toString)) dependsOn(ignoreMissingUpdate),
    moduleGraph <<= ivyReport map (absoluteReportPath.andThen(IvyGraphMLDependencies.graph)),
    moduleGraph <<= (scalaVersion, moduleGraph, filterScalaLibrary) map { (scalaV, graph, filter) =>
      if (filter)
        IvyGraphMLDependencies.ignoreScalaLibrary(scalaV, graph)
      else
        graph
    },
    moduleGraphStore <<= moduleGraph storeAs moduleGraphStore triggeredBy moduleGraph,
    asciiGraph <<= moduleGraph map IvyGraphMLDependencies.asciiGraph,
    dependencyGraph <<= InputTask(shouldForceParser) { force =>
      (force, moduleGraph, streams) map  { (force, graph, streams) =>
        if (force || graph.nodes.size < 15) {
          streams.log.info(IvyGraphMLDependencies.asciiGraph(graph))
          streams.log.info("\n\n")
          streams.log.info("Note: The old tree layout is still available by using `dependency-tree`")
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
    dependencyDotFile <<= target / "dependencies-%s.dot".format(config.toString),
    dependencyDot <<= dependencyDotTask,
    dependencyDotHead := """digraph "dependency-graph" {
          |    graph[rankdir="LR"]
          |    node [
          |        shape="record"
          |    ]
          |    edge [
          |        arrowtail="none"
          |    ]""".stripMargin,
    dependencyDotNodeLabel := { (organisation : String, name : String, version : String) =>
         """<%s<BR/><B>%s</B><BR/>%s>""".format(organisation, name, version)
    },
    whatDependsOn <<= InputTask(artifactIdParser) { module =>
      (module, streams, moduleGraph) map { (module, streams, graph) =>
        streams.log.info(IvyGraphMLDependencies.asciiTree(IvyGraphMLDependencies.reverseGraphStartingAt(graph, module)))
      }
    },
    licenseInfo <<= (moduleGraph, streams) map showLicenseInfo
  ))

  def printAsciiGraphTask =
    (streams, asciiGraph) map (_.log.info(_))

  def dependencyGraphMLTask =
    (moduleGraph, dependencyGraphMLFile, streams) map { (graph, resultFile, streams) =>
      IvyGraphMLDependencies.saveAsGraphML(graph, resultFile.getAbsolutePath)
      streams.log.info("Wrote dependency graph to '%s'" format resultFile)
      resultFile
    }
  def dependencyDotTask =
    (moduleGraph, dependencyDotHead, dependencyDotNodeLabel, dependencyDotFile, streams).
    map { (graph, dotHead, nodeLabelFormation, outFile, streams) =>
      val resultFile = IvyGraphMLDependencies.saveAsDot(graph, dotHead, nodeLabelFormation, outFile)
      streams.log.info("Wrote dependency graph to '%s'" format resultFile)
      resultFile
    }
  def absoluteReportPath = (file: File) => file.getAbsolutePath

  def print(key: TaskKey[String]) =
    (streams, key) map (_.log.info(_))

  def showLicenseInfo(graph: ModuleGraph, streams: TaskStreams) {
    val output =
      graph.nodes.filter(_.isUsed).groupBy(_.license).toSeq.sortBy(_._1).map {
        case (license, modules) =>
          license.getOrElse("No license specified")+"\n"+
          modules.map(_.id.idString formatted "\t %s").mkString("\n")
      }.mkString("\n\n")
    streams.log.info(output)
  }

  import Project._
  val shouldForceParser: State => Parser[Boolean] = { (state: State) =>
    import complete.DefaultParsers._

    (Space ~> token("--force")).?.map(_.isDefined)
  }

  import IvyGraphMLDependencies.ModuleId

  val artifactIdParser: Initialize[State => Parser[ModuleId]] =
    resolvedScoped { ctx => (state: State) =>
      val graph =  loadFromContext(moduleGraphStore, ctx, state) getOrElse ModuleGraph(Nil, Nil)

      import complete.DefaultParsers._
      import Compat._

      def moduleFrom(modules: Seq[ModuleId]) =
        modules.map { m =>
          (token(m.name) ~ Space ~ token(m.version)).map(_ => m)
        }.reduce(_ | _)

      graph.nodes.map(_.id).groupBy(_.organisation).map {
        case (org, modules) =>
          Space ~ token(org) ~ Space ~> moduleFrom(modules)
      }.reduceOption(_ | _).getOrElse {
        (Space ~> token(StringBasic, "organization") ~ Space ~ token(StringBasic, "module") ~ Space ~ token(StringBasic, "version") ).map { case ((((org, _), mod), _), version) =>
          ModuleId(org, mod, version)
        }
      }
    }

  def crossName(ivyModule: IvySbt#Module) =
    ivyModule.moduleSettings match {
      case ic: InlineConfiguration => ic.module.name
      case _ =>
        throw new IllegalStateException("sbt-dependency-graph plugin currently only supports InlineConfiguration of ivy settings (the default in sbt)")
    }

  val VersionPattern = """(\d+)\.(\d+)\.(\d+)(?:-(.*))?""".r
  object Version {
    def unapply(str: String): Option[(Int, Int, Int, Seq[String])] = str match {
      case VersionPattern(major, minor, fix, appendix) => Some((major.toInt, minor.toInt, fix.toInt, Option(appendix).toSeq))
      case _ => None
    }
  }
}